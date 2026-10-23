package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
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
 * "Continue reading" sync: the only thing that goes to the cloud is where you stopped in each book (reading
 * position). Document details and files are no longer sent or fetched; a book reaches another device through
 * Backup and Restore (which keeps the document id the positions are filed under). Old queue items of those kinds
 * are simply finished. A document that is still cloud-only from earlier versions keeps being downloaded, so no
 * book is ever stranded. Safe to run twice or be cancelled: items are only completed by the version that was read.
 */
@Singleton
class ReadingSyncProcessor @Inject constructor(
    private val syncDao: SyncItemDao,
    private val auth: AuthRepository,
    private val settings: DataStore<Preferences>,
    private val reading: ReadingPushStep,
    private val readingPull: ReadingPullStep,
    private val downloads: DownloadStep,
) {
    private val lock = Mutex()

    suspend fun run(now: () -> Long = System::currentTimeMillis): SyncRunResult = lock.withLock {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid
        when {
            settings.data.first()[SyncPrefs.PAUSED] == true -> SyncRunResult.PAUSED
            uid == null -> SyncRunResult.NOT_SIGNED_IN
            else -> {
                queueReadingForNewAccount(uid, now())
                val pushed = drain(uid, now)
                val pulled = readingPull.pull(uid)
                val downloaded = downloads.run()
                if (pushed == SyncRunResult.DONE && pulled && downloaded) SyncRunResult.DONE else SyncRunResult.WAIT
            }
        }
    }

    /** A different (or first) account: send every position once, and look at its cloud from the start. */
    private suspend fun queueReadingForNewAccount(uid: String, now: Long) {
        if (settings.data.first()[LAST_UID] == uid) return
        readingPull.reset()
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
        val waiting = syncDao.getDue(SyncItemState.FAILED, Long.MAX_VALUE, 1).isNotEmpty()
        return if (waiting) SyncRunResult.WAIT else SyncRunResult.DONE
    }

    /** false = a transient failure: stop this run (the network is probably down; do not hammer every item). */
    private suspend fun processOne(uid: String, item: SyncItemEntity, now: () -> Long): Boolean {
        val claimed = syncDao.claim(item.syncId, item.updatedAt, SyncItemState.RUNNING, SyncItemState.FAILED)
        if (claimed == 0) return true // changed or finished meanwhile; the next batch sees the current row
        val outcome = if (item.entityType == SyncEntityType.READING) reading.sync(uid, item) else RemoteResult.OK
        val attempts = item.attempts + 1
        when (outcome) {
            RemoteResult.OK -> syncDao.complete(item.syncId, item.updatedAt)
            RemoteResult.RETRY -> syncDao.markRetry(
                item.syncId,
                item.updatedAt,
                attempts,
                now() + SyncBackoff.delayMs(attempts),
                ERROR_RETRY,
            )
            else -> syncDao.markFailed(item.syncId, item.updatedAt, attempts, ERROR_DENIED)
        }
        return outcome != RemoteResult.RETRY
    }

    private companion object {
        val LAST_UID = stringPreferencesKey("sync_last_uid")
        const val BATCH_SIZE = 20
        const val ERROR_DENIED = "rejected"
        const val ERROR_RETRY = "temporary failure"
    }
}
