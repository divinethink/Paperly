package com.paperly.app.feature.scanner

import android.database.SQLException
import android.net.Uri
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanEditorSettings
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.ScanPageInfo
import com.paperly.app.domain.scanner.ScanPageRepository
import com.paperly.app.domain.scanner.StagedScan
import com.paperly.app.domain.scanner.canDeleteAll
import com.paperly.app.domain.scanner.copiedAs
import com.paperly.app.domain.scanner.copiedAt
import com.paperly.app.domain.scanner.isEdited
import com.paperly.app.domain.scanner.newScanTitle
import com.paperly.app.domain.scanner.withoutAll
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One row of the Move/Copy picker. [enabled] = the scan kept its originals, so pages can be added to it. */
data class TransferTarget(val documentId: String, val title: String, val enabled: Boolean)

/**
 * DONE = copied and checked. PARTIAL = the pages are there but crop/filter or notes did not fully come along (a Move
 * then keeps the originals). FAILED = nothing changed (a new document that was already made is the only exception
 * and is a plain, valid copy).
 */
enum class TransferResult { DONE, PARTIAL, NOT_EDITABLE, DUPLICATE, FAILED }

/**
 * Step F data work: copies chosen pages (their kept original picture + crop/rotation/filter + title/note) into another
 * scanned document or a new one, and removes pages from the source after a Move. Everything goes through the existing
 * safe paths ([ScanEditRepository.apply]: temp file, verify, backup, atomic replace; `stageGallery` + `importScan`
 * for a new document), so a failure leaves the source and the target as they were. No schema change.
 */
@Singleton
@Suppress("LongParameterList") // Hilt collaborators
class ScanPageTransfer @Inject constructor(
    private val editor: ScanEditRepository,
    private val notes: ScanPageRepository,
    private val documents: DocumentRepository,
    private val folderTarget: ScanFolderTarget,
    private val ocr: ScanOcrRepository,
    private val editorSettings: ScanEditorSettings,
    private val outputSettings: ScanOutputSettings,
) {
    /** Editor pages of [documentId], or null when the page editor is off or the scan has no kept originals. */
    suspend fun pagesOf(documentId: String): List<ScanEditPage>? =
        if (editorSettings.enabled.first()) editor.load(documentId) else null

    /** Scanned documents, as picker rows. A Move cannot target its own document. */
    suspend fun targets(sourceId: String, forMove: Boolean): List<TransferTarget> {
        val scans = documents.observeDocuments().first()
            .filter { it.type == DocumentType.SCANNED_PDF && !(forMove && it.id == sourceId) }
        return withContext(Dispatchers.IO) {
            scans.map { TransferTarget(it.id, it.title, editor.sourceFile(it.id, 0) != null) }
        }
    }

    /** Copies [picks] (pages of [sourceId], in order) to [targetId], or to a new document when it is null. */
    suspend fun copy(sourceId: String, picks: List<ScanEditPage>, targetId: String?): TransferResult {
        val infos = picks.map { notes.observe(sourceId, it.index).first() }
        val files = withContext(Dispatchers.IO) { picks.map { editor.sourceFile(sourceId, it.index) } }
        if (files.any { it == null }) return TransferResult.FAILED
        val uris = files.filterNotNull().map { Uri.fromFile(it).toString() }
        return if (targetId == null) {
            copyToNew(sourceId, picks, uris, infos)
        } else {
            copyToExisting(targetId, picks, uris, infos)
        }
    }

    /** Move's second half: the source loses [positions] (same safe rebuild as the grid's Delete). */
    suspend fun remove(sourceId: String, positions: Set<Int>): Boolean {
        val stored = pagesOf(sourceId)
        return stored != null && stored.canDeleteAll(positions) &&
            editor.apply(sourceId, stored.withoutAll(positions), outputSettings.size.first()) == ScanEditResult.SAVED
    }

    private suspend fun copyToExisting(
        targetId: String,
        picks: List<ScanEditPage>,
        uris: List<String>,
        infos: List<ScanPageInfo>,
    ): TransferResult {
        val stored = pagesOf(targetId) ?: return TransferResult.NOT_EDITABLE
        try {
            val staged = editor.stageAdded(targetId, uris)
            val added = staged?.mapIndexed { i, page -> picks[i].copiedAs(page.added.orEmpty()) }
            val saved = added != null && added.size == picks.size &&
                editor.apply(targetId, stored + added, outputSettings.size.first()) == ScanEditResult.SAVED
            return when {
                !saved -> TransferResult.FAILED
                editor.load(targetId)?.size != stored.size + picks.size -> TransferResult.PARTIAL
                copyNotes(targetId, stored.size, infos) -> TransferResult.DONE
                else -> TransferResult.PARTIAL
            }
        } finally {
            editor.discardStaged(targetId)
        }
    }

    private suspend fun copyToNew(
        sourceId: String,
        picks: List<ScanEditPage>,
        uris: List<String>,
        infos: List<ScanPageInfo>,
    ): TransferResult {
        val staged = editor.stageGallery(uris) ?: return TransferResult.FAILED
        val imported = documents.importScan(staged.pdfUri, newScanTitle())
        if (imported !is ImportResult.Success) {
            dropStaged(staged)
            return if (imported is ImportResult.Duplicate) TransferResult.DUPLICATE else TransferResult.FAILED
        }
        return finishNew(imported.documentId, sourceId, picks, staged, infos)
    }

    /** The new document exists (unedited pictures). Keeps the originals, applies crop/filter, then the notes. */
    private suspend fun finishNew(
        newId: String,
        sourceId: String,
        picks: List<ScanEditPage>,
        staged: StagedScan,
        infos: List<ScanPageInfo>,
    ): TransferResult {
        documents.getDocument(sourceId)?.folderId?.let { folderTarget.moveInto(newId, it) }
        editor.storeSources(newId, staged.pageUris)
        val ready = editor.load(newId)?.size == picks.size
        val editsOk = !picks.any { it.isEdited() } || (ready && applyEdits(newId, picks))
        val notesOk = copyNotes(newId, 0, infos)
        ocr.schedule(newId)
        return if (ready && editsOk && notesOk) TransferResult.DONE else TransferResult.PARTIAL
    }

    /** Rebuilds the new document with the pages' crop/rotation/filter (also stores them for the page editor). */
    private suspend fun applyEdits(newId: String, picks: List<ScanEditPage>): Boolean =
        editor.apply(newId, picks.mapIndexed { i, page -> page.copiedAt(i) }, outputSettings.size.first()) ==
            ScanEditResult.SAVED

    /** Titles/notes of the copied pages go to their new positions; pages without any are skipped. */
    private suspend fun copyNotes(documentId: String, firstPosition: Int, infos: List<ScanPageInfo>): Boolean = try {
        infos.forEachIndexed { i, info ->
            if (info != ScanPageInfo()) notes.save(documentId, firstPosition + i, info)
        }
        true
    } catch (ignored: SQLException) {
        false
    }

    /** A new document that was not made: its temporary PDF and picture copies are not needed any more. */
    private suspend fun dropStaged(staged: StagedScan) = withContext(Dispatchers.IO) {
        (listOf(staged.pdfUri) + staged.pageUris).forEach { uri ->
            Uri.parse(uri).path?.let { File(it).delete() }
        }
    }
}
