package com.paperly.app.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import com.paperly.app.core.database.AggregateDao
import com.paperly.app.core.database.DATABASE_VERSION
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.FolderDao
import com.paperly.app.core.database.FolderEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.backup.BackupRepository
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.RestoreResult
import com.paperly.app.domain.backup.RestoreSummary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val aggregateDao: AggregateDao,
    private val folderDao: FolderDao,
    private val fileStore: DocumentFileStore,
    private val extrasStore: BackupExtrasStore,
) : BackupRepository {

    /** Export/Restore never run concurrently (double-tap / retry safe). */
    private val lock = Mutex()

    private enum class Outcome { RESTORED, PRESENT, FAILED }

    override suspend fun export(targetUri: String): BackupResult = lock.withLock {
        val uri = Uri.parse(targetUri)
        try {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open target")
                val (written, skipped) = out.use { writeArchive(it) }
                val verified = resolver.openInputStream(uri)?.use { BackupArchive.verify(it) } == true
                if (verified) {
                    BackupResult.Success(written, skipped)
                } else {
                    discardPartial(uri)
                    BackupResult.Failed
                }
            }
        } catch (e: CancellationException) {
            discardPartial(uri)
            throw e
        } catch (e: Exception) {
            discardPartial(uri)
            BackupResult.Failed
        }
    }

    /** Collects the whole library and writes the archive to [out]. Returns (documents written, documents skipped). */
    internal suspend fun writeArchive(out: OutputStream): Pair<Int, Int> {
        val entities = aggregateDao.getAllActive().filter { DocumentType.isValid(it.type) }
        val sources = entities.mapNotNull { e ->
            fileStore.resolve(e.documentId)?.let { BackupSource(e.toBackupDocument(), it) }
        }
        val folders = folderDao.getAll().map {
            BackupFolder(it.folderId, it.name, it.parentFolderId, it.createdAt)
        }
        val header = BackupHeader(DATABASE_VERSION, appVersion(context), System.currentTimeMillis())
        val extras = extrasStore.collect(sources.map { it.doc.documentId }.toSet())
        BackupArchive.write(out, sources, folders, header, extras)
        return sources.size to (entities.size - sources.size)
    }

    override suspend fun restore(sourceUri: String): RestoreResult = lock.withLock {
        val tmp = File(context.cacheDir, "restore-${System.nanoTime()}.zip")
        try {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                    ?: throw IOException("Cannot open backup")
                input.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                restoreFromFile(tmp)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ZipException) {
            RestoreResult.InvalidBackup
        } catch (e: Exception) {
            RestoreResult.Failed
        } finally {
            tmp.delete()
        }
    }

    internal suspend fun restoreFromFile(file: File): RestoreResult = ZipFile(file).use { zip ->
        val manifest = BackupArchive.readManifest(zip) ?: return@use RestoreResult.InvalidBackup
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) return@use RestoreResult.NewerVersion
        manifest.folders.forEach { f ->
            if (folderDao.countById(f.folderId) == 0) {
                folderDao.insert(FolderEntity(f.folderId, f.name, f.parentFolderId, f.createdAt))
            }
        }
        val folderIds = folderDao.getAll().map { it.folderId }.toSet()
        val outcomes = manifest.documents.map { restoreOne(zip, it, folderIds, manifest.extras) }
        RestoreResult.Done(
            RestoreSummary(
                restored = outcomes.count { it == Outcome.RESTORED },
                alreadyPresent = outcomes.count { it == Outcome.PRESENT },
                failed = outcomes.count { it == Outcome.FAILED } + manifest.invalidEntries,
            ),
        )
    }

    private suspend fun restoreOne(
        zip: ZipFile,
        d: BackupDocument,
        folderIds: Set<String>,
        extras: BackupExtras,
    ): Outcome {
        // Same id (even in Trash) or same content already present: never overwrite, never duplicate.
        val present = documentDao.getById(d.documentId) != null || documentDao.findActiveByChecksum(d.checksum) != null
        if (present) return Outcome.PRESENT
        val stored = storeVerified(zip, d) ?: return Outcome.FAILED
        val inserted = insertRow(d, stored, folderIds)
        if (inserted) restoreExtras(d.documentId, extras)
        return if (inserted) Outcome.RESTORED else Outcome.FAILED
    }

    /** Only for a just-restored document. A failure here loses the notes/progress, never the document. */
    private suspend fun restoreExtras(documentId: String, extras: BackupExtras) {
        try {
            extrasStore.restore(documentId, extras)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // best effort: the document is already restored and verified
        }
    }

    /** Copy via the file store (temp, size check, atomic rename); the checksum must then match the manifest. */
    private suspend fun storeVerified(zip: ZipFile, d: BackupDocument): StoredFile? = try {
        val stored = BackupArchive.openDocument(zip, d.documentId)?.use { fileStore.storeFrom(it, d.documentId) }
        if (stored != null && stored.checksum != d.checksum) {
            cleanup(d.documentId)
            null
        } else {
            stored
        }
    } catch (e: CancellationException) {
        cleanup(d.documentId)
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun insertRow(d: BackupDocument, stored: StoredFile, folderIds: Set<String>): Boolean {
        val entity = DocumentEntity(
            documentId = d.documentId,
            title = d.title,
            type = d.type,
            sizeBytes = stored.sizeBytes,
            checksum = d.checksum,
            localUri = stored.path,
            folderId = d.folderId?.takeIf { it in folderIds },
            tags = d.tags,
            isFavorite = d.isFavorite,
            createdAt = d.createdAt,
            updatedAt = d.updatedAt,
            lastOpenedAt = d.lastOpenedAt,
        )
        val inserted = try {
            documentDao.insert(entity) != -1L
        } catch (e: CancellationException) {
            cleanup(d.documentId)
            throw e
        } catch (e: Exception) {
            false
        }
        if (!inserted) cleanup(d.documentId) // no orphan file when the row was not written
        return inserted
    }

    private suspend fun cleanup(id: String) {
        withContext(NonCancellable) { fileStore.delete(id) }
    }

    private suspend fun discardPartial(uri: Uri) {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (e: Exception) {
                // best effort: failing to delete a partial file is not fatal
            }
        }
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: PackageManager.NameNotFoundException) {
    ""
}

private fun DocumentEntity.toBackupDocument() = BackupDocument(
    documentId = documentId,
    title = title,
    type = type,
    sizeBytes = sizeBytes,
    checksum = checksum,
    folderId = folderId,
    tags = tags.orEmpty(),
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupDataModule {
    @Binds
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
