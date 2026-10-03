package com.paperly.app.domain.sync

import kotlinx.coroutines.flow.Flow

/** [synced] of [total] active documents have their file in the cloud. */
data class FileSyncCounts(val synced: Int, val total: Int)

/** [pending] = waiting or running, [failed] = rejected (can be retried), [conflicts] = need the user's decision. */
data class SyncQueueCounts(val pending: Int, val failed: Int, val conflicts: Int)

/** A document whose cloud copy was changed elsewhere while it was changed here too. */
data class ConflictInfo(val documentId: String, val title: String)

interface SyncStatus {
    val fileCounts: Flow<FileSyncCounts>
    val queueCounts: Flow<SyncQueueCounts>
    val conflicts: Flow<List<ConflictInfo>>
}
