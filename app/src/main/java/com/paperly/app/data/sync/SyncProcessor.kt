package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteResult
import com.paperly.app.domain.sync.SyncBackoff
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncRunResult {
    /** Nothing left to do. */
    DONE,

    /** Items are waiting for a retry time: run again later. */
    WAIT,

    /** No signed-in account: the queue is left untouched. */
    NOT_SIGNED_IN,
}

/**
 * Drains the sync queue (metadata only; file upload arrives in P8-D). Safe to run twice or be cancelled:
 * remote writes are idempotent and an item is only completed by the version that was read.
 */
@Singleton
class SyncProcessor @Inject constructor(
    private val syncDao: SyncItemDao,
    private val documentDao: DocumentDao,
    private val remote: RemoteMetadataStore,
    private val auth: AuthRepository,
    private val settings: DataStore<Preferences>,
) {
    private val lock = Mutex()

    suspend fun run(now: () -> Long = System::currentTimeMillis): SyncRunResult = lock.withLock {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid
        if (uid == null) {
            SyncRunResult.NOT_SIGNED_IN
        } else {
            queueEverythingForNewAccount(uid, now())
            drain(uid, now)
        }
    }

    /** A different (or first) account has none of this library in its cloud yet: queue all documents once. */
    private suspend fun queueEverythingForNewAccount(uid: String, now: Long) {
        if (settings.data.first()[LAST_UID] == uid) return
        syncDao.enqueueAllDocuments(now)
        settings.edit { it[LAST_UID] = uid } // after the enqueue: a crash in between just repeats it (idempotent)
    }

    private suspend fun drain(uid: String, now: () -> Long): SyncRunResult {
        var batch = syncDao.getDue(SyncItemState.FAILED, now(), BATCH_SIZE)
        while (batch.isNotEmpty()) {
            for (item in batch) {
                if (!processOne(uid, item, now)) return SyncRunResult.WAIT
            }
            batch = syncDao.getDue(SyncItemState.FAILED, now(), BATCH_SIZE)
        }
        return if (syncDao.countWaiting(SyncItemState.FAILED) > 0) SyncRunResult.WAIT else SyncRunResult.DONE
    }

    /** false = a transient failure: stop this run (the network is probably down; do not hammer every item). */
    private suspend fun processOne(uid: String, item: SyncItemEntity, now: () -> Long): Boolean {
        val claimed = syncDao.claim(item.syncId, item.updatedAt, SyncItemState.RUNNING, SyncItemState.FAILED)
        if (claimed == 0) return true // changed or finished meanwhile; the next batch sees the current row
        val outcome = execute(uid, item)
        val attempts = item.attempts + 1
        when (outcome) {
            RemoteResult.OK -> syncDao.complete(item.syncId, item.updatedAt)
            RemoteResult.DENIED -> syncDao.markState(
                syncId = item.syncId,
                version = item.updatedAt,
                state = SyncItemState.FAILED,
                attempts = attempts,
                nextRetryAt = null,
                lastError = ERROR_DENIED,
            )
            RemoteResult.RETRY -> syncDao.markState(
                syncId = item.syncId,
                version = item.updatedAt,
                state = SyncItemState.RETRYING,
                attempts = attempts,
                nextRetryAt = now() + SyncBackoff.delayMs(attempts),
                lastError = ERROR_RETRY,
            )
        }
        return outcome != RemoteResult.RETRY
    }

    private suspend fun execute(uid: String, item: SyncItemEntity): RemoteResult {
        // Unknown kind of item: rejected, never retried blindly.
        if (item.entityType != SyncEntityType.DOCUMENT) return RemoteResult.DENIED
        // A PUT whose row has since been removed becomes a remote delete; DELETE never needs the row.
        val entity = if (item.operation == SyncOperation.PUT) documentDao.getById(item.entityId) else null
        return if (entity == null) {
            remote.deleteDocument(uid, item.entityId)
        } else {
            remote.putDocument(uid, entity.toMeta())
        }
    }

    private companion object {
        val LAST_UID = stringPreferencesKey("sync_last_uid")
        const val BATCH_SIZE = 20
        const val ERROR_DENIED = "rejected"
        const val ERROR_RETRY = "temporary failure"
    }
}
