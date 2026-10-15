package com.paperly.app.data.scanner

import android.net.Uri
import com.paperly.app.core.database.ScanPageEditDao
import com.paperly.app.core.database.ScanPageEditEntity
import com.paperly.app.core.database.ScanPageRemapDao
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.PdfHighlightFileSync
import com.paperly.app.domain.scanner.ADDED_INDEX
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.ScanOutputSize
import com.paperly.app.domain.scanner.ScanQuad
import com.paperly.app.domain.scanner.StagedScan
import com.paperly.app.domain.scanner.isStructural
import com.paperly.app.domain.scanner.normalizeRotation
import com.paperly.app.domain.scanner.positionMap
import com.paperly.app.domain.scanner.validPlan
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * S2 data side. A rebuild never touches the Library file until the new PDF is verified and backed up
 * ([ScanPdfReplacer]); the DB rows follow only after the file was replaced.
 */
@Singleton
@Suppress("TooManyFunctions", "LongParameterList") // Hilt-injected collaborators; one method per interface operation
class ScanEditRepositoryImpl @Inject constructor(
    private val sources: ScanSourceStore,
    private val edits: ScanPageEditDao,
    private val remapDao: ScanPageRemapDao,
    private val files: LibraryPdfFiles,
    private val replacer: ScanPdfReplacer,
    private val builder: ScanPdfBuilder,
    private val highlightSync: PdfHighlightFileSync,
) : ScanEditRepository {
    private val lock = Mutex()

    override suspend fun storeSources(documentId: String, pageUris: List<String>) {
        withContext(Dispatchers.IO) { sources.keep(documentId, pageUris) }
    }

    override fun sourceFile(documentId: String, index: Int): File? = sources.file(documentId, index)

    override fun pageFile(documentId: String, page: ScanEditPage): File? =
        if (page.added != null) sources.stagedFile(documentId, page.added) else sources.file(documentId, page.index)

    override suspend fun stageAdded(documentId: String, uris: List<String>): List<ScanEditPage>? =
        withContext(Dispatchers.IO) {
            if (uris.isEmpty()) return@withContext null
            sources.stageAdded(documentId, uris)?.map { ScanEditPage(index = ADDED_INDEX, added = it) }
        }

    override fun discardStaged(documentId: String) = sources.discardStaged(documentId)

    override suspend fun load(documentId: String): List<ScanEditPage>? = withContext(Dispatchers.IO) {
        val count = sources.count(documentId)
        if (count == 0) return@withContext null
        val saved = edits.getForDocument(documentId).associateBy { it.pageIndex }
        val locked = edits.getAnnotationLocators(documentId).mapNotNull { it.toIntOrNull() }.toSet()
        List(count) { i ->
            val row = saved[i]
            ScanEditPage(
                index = i,
                rotation = normalizeRotation(row?.rotation ?: 0),
                quad = ScanQuad.fromJson(row?.cornersJson) ?: ScanQuad.FULL,
                filter = ScanFilter.parse(row?.filter),
                locked = i in locked,
            )
        }
    }

    override suspend fun apply(documentId: String, pages: List<ScanEditPage>, size: ScanOutputSize): ScanEditResult =
        withContext(Dispatchers.IO) { lock.withLock { guarded(documentId, pages, size) } }

    private suspend fun guarded(documentId: String, pages: List<ScanEditPage>, size: ScanOutputSize): ScanEditResult {
        val file = files.writableFile(documentId)
        val stored = load(documentId)
        if (file == null || stored == null || !validPlan(pages, stored)) return ScanEditResult.FAILED
        // the lock rule is enforced here too: a locked page keeps its stored geometry whatever the caller sent
        val effective = pages.map { page ->
            if (page.isAdded) {
                page
            } else {
                val old = stored[page.index]
                if (page.locked || old.locked) keepGeometry(page, old) else page
            }
        }
        val tmp = File(file.parentFile, "${file.name}$TMP_SUFFIX") // ".tmp": OrphanFiles removes a stale one
        val structural = effective.isStructural(stored.size)
        return try {
            builder.build(tmp, effective, size) { page ->
                pageFile(documentId, page) ?: throw IOException("Missing page image")
            }
            if (structural) {
                restructure(documentId, file, tmp, effective)
            } else {
                replacer.replace(documentId, file, tmp, effective.size)
                saveRows(documentId, effective)
            }
            highlightSync.schedule(documentId) // re-puts the app's highlights into the new file (setting permitting)
            ScanEditResult.SAVED
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: OutOfMemoryError) {
            ScanEditResult.OUT_OF_MEMORY
        } catch (e: Exception) {
            ScanEditResult.FAILED
        } finally {
            tmp.delete() // no-op after the move; removes a half-written temp otherwise
        }
    }

    /**
     * S4: pages were added, deleted or reordered. Order: verified temp PDF + backup -> new image folder aside ->
     * ONE transaction (rows follow the new order, then the file is swapped in last) -> image folder swap.
     * Any failure before the commit leaves file, rows and images exactly as they were.
     */
    private suspend fun restructure(documentId: String, file: File, tmp: File, pages: List<ScanEditPage>) {
        val prepared = replacer.prepare(file, tmp, pages.size)
        var swapped = false
        try {
            sources.prepareNext(documentId, pages.map { pageFile(documentId, it) ?: throw IOException("Missing page") })
            remapDao.remap(documentId, pages.positionMap(), pages.size, editRows(documentId, pages)) {
                replacer.commitLast(documentId, file, tmp, prepared)
            }
            swapped = true
        } finally {
            if (!swapped) sources.discardNext(documentId)
        }
        withContext(NonCancellable) { sources.commitNext(documentId) }
        sources.discardStaged(documentId)
    }

    private fun keepGeometry(page: ScanEditPage, stored: ScanEditPage) =
        page.copy(rotation = stored.rotation, quad = stored.quad, locked = true)

    private fun editRows(documentId: String, pages: List<ScanEditPage>): List<ScanPageEditEntity> {
        val now = System.currentTimeMillis()
        return pages.mapIndexed { position, page ->
            ScanPageEditEntity(
                documentId = documentId,
                pageIndex = position,
                srcFile = sources.pageName(position),
                rotation = page.rotation,
                cornersJson = page.quad.takeIf { q -> q != ScanQuad.FULL }?.toJson(),
                filter = page.filter.name,
                updatedAt = now,
            )
        }
    }

    /** The file is already replaced; a failed row write only resets the editor's view to "unedited". */
    private suspend fun saveRows(documentId: String, pages: List<ScanEditPage>) {
        runCatching { edits.upsertAll(editRows(documentId, pages)) }
    }

    override suspend fun stageGallery(uris: List<String>): StagedScan? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = if (uris.isEmpty()) null else sources.stage(uris, id)
        if (dir == null) return@withContext null
        val pdf = sources.galleryPdf(id)
        val staged = try {
            builder.build(pdf, uris.indices.map { ScanEditPage(index = it) }, ScanOutputSize.MEDIUM) {
                File(dir, sources.pageName(it.index))
            }
            val pageUris = uris.indices.map { Uri.fromFile(File(dir, sources.pageName(it))).toString() }
            StagedScan(Uri.fromFile(pdf).toString(), pageUris)
        } catch (e: CancellationException) {
            discard(pdf, dir)
            throw e
        } catch (ignored: OutOfMemoryError) {
            discard(pdf, dir)
            null
        } catch (e: Exception) {
            discard(pdf, dir)
            null
        }
        staged
    }

    private fun discard(pdf: File, dir: File) {
        pdf.delete()
        dir.deleteRecursively()
    }

    private companion object {
        const val TMP_SUFFIX = ".scan.tmp"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanEditModule {
    @Binds
    abstract fun bindScanEditRepository(impl: ScanEditRepositoryImpl): ScanEditRepository
}
