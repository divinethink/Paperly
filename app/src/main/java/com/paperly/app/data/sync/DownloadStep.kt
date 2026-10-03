package com.paperly.app.data.sync

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.StorageState
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.RemoteFileStore
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P8-E2: downloads the file of every pulled document (CLOUD_ONLY). The file only becomes visible after its checksum
 * matched (DocumentFileStore writes to a temp file first); a failed download leaves the row as it was.
 */
@Singleton
class DownloadStep @Inject constructor(
    private val cloud: CloudSyncDao,
    private val files: DocumentFileStore,
    private val remote: RemoteFileStore,
) {
    /** false = stopped early (offline, or Drive access still to be allowed): run again later. */
    suspend fun run(): Boolean {
        for (row in cloud.awaitingDownload(StorageState.CLOUD_ONLY)) {
            if (files.resolve(row.documentId) != null) {
                // A file is already here (e.g. restored from a backup): it is the original; normal upload takes over.
                cloud.setStorageState(row.documentId, StorageState.LOCAL_ONLY)
                continue
            }
            val target = StoreTarget(files, row.documentId)
            when (val result = remote.download(row.documentId, row.checksum, target)) {
                is FileSyncResult.Done ->
                    cloud.setDownloaded(row.documentId, target.path, result.cloudRef, StorageState.SYNCED)
                FileSyncResult.Retry, FileSyncResult.NeedsConsent -> return false
                // No cloud copy (yet) or refused: the row stays CLOUD_ONLY and is tried again on a later run.
                FileSyncResult.NotFound, FileSyncResult.Denied -> Unit
            }
        }
        return true
    }

    private class StoreTarget(private val files: DocumentFileStore, private val documentId: String) : DownloadTarget {
        var path: String? = null

        override suspend fun store(input: InputStream): String {
            val stored = files.storeFrom(input, documentId)
            path = stored.path
            return stored.checksum
        }

        override suspend fun discard() {
            files.delete(documentId)
            path = null
        }
    }
}
