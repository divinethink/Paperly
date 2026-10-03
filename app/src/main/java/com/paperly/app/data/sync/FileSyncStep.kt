package com.paperly.app.data.sync

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.StorageState
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The file half of sync: uploads the stored original of a document and records the result on its row. */
@Singleton
class FileSyncStep @Inject constructor(
    private val documents: DocumentDao,
    private val cloud: CloudSyncDao,
    private val files: DocumentFileStore,
    private val remote: RemoteFileStore,
) {
    /** Documents that still have no cloud copy get a file item (items that already exist are left alone). */
    suspend fun enqueueMissing(now: Long) = cloud.enqueueMissingFiles(now)

    /** A different account signed in: forget what the previous account's Drive held. */
    suspend fun forgetCloudCopies() {
        cloud.forgetCloudCopies(StorageState.LOCAL_ONLY)
        remote.forgetUploads()
    }

    suspend fun sync(documentId: String): RemoteResult {
        val doc = documents.getById(documentId)
        val file = files.resolve(documentId)
        return when {
            // Gone, in Trash (a restore queues it again) or already uploaded: nothing to do.
            doc == null || doc.deletedAt != null || doc.cloudRef != null -> RemoteResult.OK
            file == null -> RemoteResult.DENIED
            else -> upload(doc, file)
        }
    }

    private suspend fun upload(doc: DocumentEntity, file: File): RemoteResult {
        cloud.setStorageState(doc.documentId, StorageState.RUNNING)
        return when (val result = remote.upload(doc.documentId, file, doc.checksum)) {
            is FileSyncResult.Done -> {
                cloud.setSynced(doc.documentId, result.cloudRef, StorageState.SYNCED)
                RemoteResult.OK
            }
            FileSyncResult.Retry, FileSyncResult.NotFound -> settle(doc, StorageState.RETRYING, RemoteResult.RETRY)
            FileSyncResult.Denied -> settle(doc, StorageState.LOCAL_ONLY, RemoteResult.DENIED)
            FileSyncResult.NeedsConsent -> settle(doc, StorageState.QUEUED, RemoteResult.DEFERRED)
        }
    }

    private suspend fun settle(doc: DocumentEntity, state: String, result: RemoteResult): RemoteResult {
        cloud.setStorageState(doc.documentId, state)
        return result
    }
}
