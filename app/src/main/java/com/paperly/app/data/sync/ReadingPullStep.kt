package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.ReaderDao
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.syncIdOf
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.ReadingPullAction
import com.paperly.app.domain.sync.ReadingPullDecision
import com.paperly.app.domain.sync.RemoteReadingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P8-E4: brings reading positions down, after the documents themselves (a position needs its document row).
 * The newer position wins ([ReadingPullDecision]); a position waiting to be sent is never overwritten. The
 * Reader's resume logic reads the same `reading_state` row as before, so no screen changes. Applying twice
 * changes nothing, and an applied position is never queued back to the cloud.
 */
@Singleton
class ReadingPullStep @Inject constructor(
    private val documents: DocumentDao,
    private val reader: ReaderDao,
    private val syncDao: SyncItemDao,
    private val remote: RemoteReadingStore,
    private val settings: DataStore<Preferences>,
) {
    /** A different account signed in: the old account's positions are irrelevant. */
    suspend fun reset() {
        settings.edit { it.remove(LAST_PULL) }
    }

    /** false = the cloud could not be reached; run again later. */
    suspend fun pull(uid: String): Boolean {
        val last = settings.data.first()[LAST_PULL] ?: 0L
        // Looks back a day (slow device clocks); positions seen again are skipped by the decision rule.
        val metas = remote.fetchReadingSince(uid, (last - LOOKBACK_MS).coerceAtLeast(0L)) ?: return false
        var newest = last
        var blockedAt: Long? = null
        for (meta in metas.filter { SAFE_DOCUMENT_ID.matches(it.documentId) }) {
            if (documents.getById(meta.documentId) == null) {
                blockedAt = minOf(blockedAt ?: meta.updatedAt, meta.updatedAt)
            } else {
                applyOne(meta)
                newest = maxOf(newest, meta.updatedAt)
            }
        }
        // A position whose document has not arrived (yet) must stay inside the next fetch window.
        val marker = blockedAt?.let { minOf(newest, it - 1) } ?: newest
        if (marker > last) settings.edit { it[LAST_PULL] = marker }
        return true
    }

    private suspend fun applyOne(meta: ReadingMeta) {
        val locator = syncableLocator(meta.locator) ?: return // unusable cloud data is ignored, never stored
        val local = reader.getState(meta.documentId)
        val pending = syncDao.countItem(syncIdOf(SyncEntityType.READING, meta.documentId)) > 0
        if (ReadingPullDecision.decide(local?.lastOpenedAt, pending, meta) == ReadingPullAction.APPLY) {
            reader.upsertState(
                ReadingStateEntity(meta.documentId, locator, meta.progressPercent.coerceIn(0f, 1f), meta.updatedAt),
            )
        }
    }

    private companion object {
        val LAST_PULL = longPreferencesKey("sync_last_pull_reading")
        const val LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
