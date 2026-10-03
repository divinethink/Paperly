package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.SyncBaseEntity
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.model.SYNC_ERROR_CONFLICT
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

/** `lastError` of an item parked until the user allows Drive access; `SyncQueue.resumeDeferred` releases them. */
internal const val SYNC_ERROR_NEEDS_ACCESS = "needs drive access"

enum class SyncRunResult {
    /** Nothing left to do. */
    DONE,

    /** Items are waiting for a retry time: run again later. */
    WAIT,

    /** No signed-in account: the queue is left untouched. */
    NOT_SIGNED_IN,

    /** The user paused sync: the queue is left untouched. */
    PAUSED,
}

/**
 * Drains the sync queue: document metadata (Firestore) and document files (Drive). Safe to run twice or be
 * cancelled: remote writes are idempotent and an item is only completed by the version that was read.
 */
@Suppress("LongParameterList") // Hilt DI collaborators
@Singleton
class SyncProcessor @Inject constructor(
    private val syncDao: SyncItemDao,
    private val documentDao: DocumentDao,
    private val remote: RemoteMetadataStore,
    private val auth: AuthRepository,
    private val settings: DataStore<Preferences>,
    private val files: FileSyncStep,
    private val pull: PullStep,
    private val downloads: DownloadStep,
    private val reading: ReadingPushStep,
) {
    private val lock = Mutex()

    suspend fun run(now: () -> Long = System::currentTimeMillis): SyncRunResult = lock.withLock {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid
        when {
            settings.data.first()[SyncPrefs.PAUSED] == true -> SyncRunResult.PAUSED
            uid == null -> SyncRunResult.NOT_SIGNED_IN
            else -> {
                queueEverythingForNewAccount(uid, now())
                files.enqueueMissing(now())
                val pushed = drain(uid, now)
                // Pull and download even when some push items wait: they do not depend on each other.
                val pulled = pull.pull(uid)
                val downloaded = downloads.run()
                if (pushed == SyncRunResult.DONE && pulled && downloaded) SyncRunResult.DONE else SyncRunResult.WAIT
            }
        }
    }

    /** A different (or first) account has none of this library in its cloud yet: queue all documents once. */
    private suspend fun queueEverythingForNewAccount(uid: String, now: Long) {
        if (settings.data.first()[LAST_UID] == uid) return
        files.forgetCloudCopies()
        syncDao.clearBases()
        pull.reset()
        syncDao.enqueueAllDocuments(now)
        syncDao.enqueueAllReading(now)
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
        // Anything not FAILED that is still here is waiting for its retry time.
        val waiting = syncDao.getDue(SyncItemState.FAILED, Long.MAX_VALUE, 1).isNotEmpty()
        return if (waiting) SyncRunResult.WAIT else SyncRunResult.DONE
    }

    /** false = a transient failure: stop this run (the network is probably down; do not hammer every item). */
    private suspend fun processOne(uid: String, item: SyncItemEntity, now: () -> Long): Boolean {
        val claimed = syncDao.claim(item.syncId, item.updatedAt, SyncItemState.RUNNING, SyncItemState.FAILED)
        if (claimed == 0) return true // changed or finished meanwhile; the next batch sees the current row
        val outcome = execute(uid, item)
        val attempts = item.attempts + 1
        when (outcome) {
            RemoteResult.OK -> syncDao.complete(item.syncId, item.updatedAt)
            RemoteResult.DENIED -> syncDao.markFailed(item.syncId, item.updatedAt, attempts, ERROR_DENIED)
            RemoteResult.RETRY -> syncDao.markRetry(
                item.syncId,
                item.updatedAt,
                attempts,
                now() + SyncBackoff.delayMs(attempts),
                ERROR_RETRY,
            )
            // The cloud copy changed elsewhere: stop for this item (no retry, no overwrite) until the user decides.
            RemoteResult.CONFLICT -> syncDao.markFailed(item.syncId, item.updatedAt, item.attempts, SYNC_ERROR_CONFLICT)
            // Not a failure: waits for the user (attempts unchanged); the run goes on with the other items.
            RemoteResult.DEFERRED -> syncDao.markRetry(
                item.syncId,
                item.updatedAt,
                item.attempts,
                now() + DEFER_MS,
                SYNC_ERROR_NEEDS_ACCESS,
            )
        }
        return outcome != RemoteResult.RETRY
    }

    private suspend fun execute(uid: String, item: SyncItemEntity): RemoteResult = when (item.entityType) {
        SyncEntityType.DOCUMENT -> executeDocument(uid, item)
        SyncEntityType.FILE ->
            if (item.operation == SyncOperation.PUT) files.sync(item.entityId) else RemoteResult.DENIED
        SyncEntityType.READING -> reading.sync(uid, item)
        // Unknown kind of item: rejected, never retried blindly.
        else -> RemoteResult.DENIED
    }

    private suspend fun executeDocument(uid: String, item: SyncItemEntity): RemoteResult {
        // A PUT whose row has since been removed becomes a remote delete; DELETE never needs the row.
        val entity = if (item.operation == SyncOperation.PUT) documentDao.getById(item.entityId) else null
        return if (entity == null) {
            remote.deleteDocument(uid, item.entityId).also {
                if (it == RemoteResult.OK) syncDao.deleteBase(item.entityId)
            }
        } else {
            val meta = entity.toMeta()
            remote.putDocument(uid, meta, syncDao.getBase(item.entityId)).also {
                if (it == RemoteResult.OK) syncDao.setBase(SyncBaseEntity(item.entityId, meta.updatedAt))
            }
        }
    }

    private companion object {
        val LAST_UID = stringPreferencesKey("sync_last_uid")
        const val BATCH_SIZE = 20
        const val ERROR_DENIED = "rejected"
        const val ERROR_RETRY = "temporary failure"
        const val DEFER_MS = 15L * 60 * 1000
    }
}
