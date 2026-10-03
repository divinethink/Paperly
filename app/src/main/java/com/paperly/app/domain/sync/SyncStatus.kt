package com.paperly.app.domain.sync

import kotlinx.coroutines.flow.Flow

/** [synced] of [total] active documents have their file in the cloud. */
data class FileSyncCounts(val synced: Int, val total: Int)

interface SyncStatus {
    val fileCounts: Flow<FileSyncCounts>
}
