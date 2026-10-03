package com.paperly.app.data.sync

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.domain.sync.FileSyncCounts
import com.paperly.app.domain.sync.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSyncStatus @Inject constructor(dao: CloudSyncDao) : SyncStatus {
    override val fileCounts: Flow<FileSyncCounts> = dao.observeFileCounts().map { FileSyncCounts(it.synced, it.total) }
}
