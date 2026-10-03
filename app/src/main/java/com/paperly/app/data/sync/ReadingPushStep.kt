package com.paperly.app.data.sync

import com.paperly.app.core.database.ReaderDao
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.RemoteReadingStore
import com.paperly.app.domain.sync.RemoteResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P8-E4: sends one document's reading position. Reads the row at send time (the queue item only says "changed"),
 * so many page turns collapse into one write. A missing row or a locator that cannot be made safe to send is
 * simply done (OK): there is nothing to retry and nothing is ever deleted remotely by a PUT.
 */
@Singleton
class ReadingPushStep @Inject constructor(
    private val reader: ReaderDao,
    private val remote: RemoteReadingStore,
) {
    suspend fun sync(uid: String, item: SyncItemEntity): RemoteResult =
        if (item.operation == SyncOperation.DELETE) {
            remote.deleteReading(uid, item.entityId)
        } else {
            put(uid, item.entityId)
        }

    private suspend fun put(uid: String, documentId: String): RemoteResult {
        val state = reader.getState(documentId)
        val locator = state?.let { syncableLocator(it.locator) }
        return if (state == null || locator == null) {
            RemoteResult.OK
        } else {
            remote.putReading(
                uid,
                ReadingMeta(documentId, locator, state.progressPercent.coerceIn(0f, 1f), state.lastOpenedAt),
            )
        }
    }
}
