package com.paperly.app.domain.sync

import kotlinx.coroutines.flow.Flow

/** [pending] = waiting or running, [failed] = rejected (can be retried), [conflicts] = need the user's decision. */
data class SyncQueueCounts(val pending: Int, val failed: Int, val conflicts: Int)

interface SyncStatus {
    val queueCounts: Flow<SyncQueueCounts>
}
