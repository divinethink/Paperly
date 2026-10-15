package com.paperly.app.data.document

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.model.StorageState
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.sync.SyncQueue
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class LibraryPdfFilesImpl @Inject constructor(
    private val documents: DocumentDao,
    private val files: DocumentFileStore,
    private val cloud: CloudSyncDao,
    private val syncItems: SyncItemDao,
    private val queue: SyncQueue,
) : LibraryPdfFiles {

    override suspend fun writableFile(documentId: String): File? {
        val doc = documents.getById(documentId)
        val ok = doc != null && doc.deletedAt == null && doc.type in PDF_TYPES &&
            doc.storageState != StorageState.CLOUD_ONLY
        return if (ok) files.resolve(documentId) else null
    }

    override suspend fun replaced(documentId: String, checksum: String, sizeBytes: Long) {
        val now = System.currentTimeMillis()
        retry { cloud.replaceFile(documentId, checksum, sizeBytes, now, StorageState.QUEUED) }
        // A FAILED/waiting file item is re-armed (coalesced, never duplicated); sync is best-effort by design.
        runCatching { syncItems.enqueue(SyncEntityType.FILE, documentId, SyncOperation.PUT, now) }
        queue.documentChanged(documentId)
    }

    /** The file already changed on disk; the row must follow, so a failed write is tried again. */
    private suspend fun retry(block: suspend () -> Unit) {
        var last: Exception? = null
        repeat(ATTEMPTS) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw checkNotNull(last)
    }

    private companion object {
        val PDF_TYPES = setOf(DocumentType.PDF, DocumentType.SCANNED_PDF)
        const val ATTEMPTS = 3
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryPdfFilesModule {
    @Binds
    abstract fun bindLibraryPdfFiles(impl: LibraryPdfFilesImpl): LibraryPdfFiles
}
