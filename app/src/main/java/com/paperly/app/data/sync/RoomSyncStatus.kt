package com.paperly.app.data.sync

import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.domain.sync.SyncQueueCounts
import com.paperly.app.domain.sync.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSyncStatus @Inject constructor(items: SyncItemDao) : SyncStatus {
    override val queueCounts: Flow<SyncQueueCounts> =
        items.observeQueueCounts().map { SyncQueueCounts(it.pending, it.failed, it.conflicts) }
}
