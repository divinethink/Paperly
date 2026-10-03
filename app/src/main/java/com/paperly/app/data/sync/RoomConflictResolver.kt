package com.paperly.app.data.sync

import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.SyncBaseEntity
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.core.model.syncIdOf
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.sync.ConflictResolver
import com.paperly.app.domain.sync.RemoteDocument
import com.paperly.app.domain.sync.RemoteMetadataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P8-E3: carries out the user's choice for a conflict. Both choices first read the cloud's current copy; if that is
 * not possible nothing changes and the conflict stays (false). The caller schedules a sync run afterwards.
 */
@Singleton
class RoomConflictResolver @Inject constructor(
    private val syncDao: SyncItemDao,
    private val cloud: CloudSyncDao,
    private val documents: DocumentDao,
    private val remote: RemoteMetadataStore,
    private val auth: AuthRepository,
) : ConflictResolver {

    override suspend fun keepThisDevice(documentId: String): Boolean {
        val local = documents.getById(documentId) ?: return false
        val current = lookup(documentId)
        if (current == RemoteDocument.Failed) return false
        val seen = (current as? RemoteDocument.Found)?.meta?.updatedAt
        if (seen == null) syncDao.deleteBase(documentId) else syncDao.setBase(SyncBaseEntity(documentId, seen))
        // Newer than the cloud copy, so every other device takes it over on its next pull.
        val now = System.currentTimeMillis()
        cloud.setUpdatedAt(documentId, maxOf(now, local.updatedAt + 1, (seen ?: 0L) + 1))
        syncDao.enqueue(SyncEntityType.DOCUMENT, documentId, SyncOperation.PUT, now)
        return true
    }

    override suspend fun keepCloud(documentId: String): Boolean {
        val local = documents.getById(documentId) ?: return false
        when (val current = lookup(documentId)) {
            RemoteDocument.Failed -> return false
            // Deleted in the cloud: it goes to Trash here (restorable), never away for good.
            RemoteDocument.Absent -> {
                cloud.applyRemote(
                    documentId,
                    local.title,
                    local.tags,
                    local.isFavorite,
                    System.currentTimeMillis(),
                    local.updatedAt,
                )
                syncDao.deleteBase(documentId)
            }
            is RemoteDocument.Found -> {
                val m = current.meta
                cloud.applyRemote(documentId, m.title, m.tags, m.isFavorite, m.deletedAt, m.updatedAt)
                syncDao.setBase(SyncBaseEntity(documentId, m.updatedAt))
            }
        }
        syncDao.dropConflict(syncIdOf(SyncEntityType.DOCUMENT, documentId))
        return true
    }

    private suspend fun lookup(documentId: String): RemoteDocument {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid ?: return RemoteDocument.Failed
        return remote.fetchDocument(uid, documentId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConflictModule {
    @Binds
    abstract fun bindConflictResolver(impl: RoomConflictResolver): ConflictResolver
}
