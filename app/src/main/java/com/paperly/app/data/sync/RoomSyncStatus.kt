package com.paperly.app.data.sync

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.domain.sync.ConflictInfo
import com.paperly.app.domain.sync.FileSyncCounts
import com.paperly.app.domain.sync.SyncQueueCounts
import com.paperly.app.domain.sync.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSyncStatus @Inject constructor(cloud: CloudSyncDao, items: SyncItemDao) : SyncStatus {
    override val fileCounts: Flow<FileSyncCounts> = cloud.observeFileCounts().map { FileSyncCounts(it.synced, it.total) }

    override val queueCounts: Flow<SyncQueueCounts> =
        items.observeQueueCounts().map { SyncQueueCounts(it.pending, it.failed, it.conflicts) }

    override val conflicts: Flow<List<ConflictInfo>> =
        items.observeConflicts().map { rows -> rows.map { ConflictInfo(it.documentId, it.title) } }
}
