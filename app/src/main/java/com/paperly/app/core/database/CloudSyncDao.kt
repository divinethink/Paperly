package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.core.model.syncIdOf
import kotlinx.coroutines.flow.Flow

/** Aggregate over all active documents (no LIMIT: a truncated count would be wrong). */
data class FileCountsRow(val synced: Int, val total: Int)

/**
 * P8-D: cloud-file bookkeeping. No schema change: it only uses columns that already exist
 * (`documents.cloudRef/storageState`, `sync_items`). None of these writes touches `updatedAt`, so they never
 * look like a user edit and never re-queue document metadata.
 */
@Dao
abstract class CloudSyncDao {
    @Query("UPDATE documents SET storageState = :state WHERE documentId = :id")
    abstract suspend fun setStorageState(id: String, state: String): Int

    @Query("UPDATE documents SET cloudRef = :cloudRef, storageState = :state WHERE documentId = :id")
    abstract suspend fun setSynced(id: String, cloudRef: String, state: String): Int

    /** Another account signed in: the old account's Drive ids mean nothing here. */
    @Query("UPDATE documents SET cloudRef = NULL, storageState = :state")
    abstract suspend fun forgetCloudCopies(state: String): Int

    @Query("SELECT documentId FROM documents WHERE cloudRef IS NULL AND deletedAt IS NULL")
    abstract suspend fun idsWithoutCloudCopy(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItem(entity: SyncItemEntity): Long

    /**
     * One FILE item per document without a cloud copy. IGNORE keeps an existing item as it is, so a run never
     * resets the retry/backoff of an item that is already waiting.
     */
    @Transaction
    open suspend fun enqueueMissingFiles(now: Long) {
        idsWithoutCloudCopy().forEach { id ->
            insertItem(
                SyncItemEntity(
                    syncId = syncIdOf(SyncEntityType.FILE, id),
                    entityType = SyncEntityType.FILE,
                    entityId = id,
                    operation = SyncOperation.PUT,
                    state = SyncItemState.QUEUED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Items parked with [reason] (e.g. waiting for Drive consent) become due again. */
    @Query(
        "UPDATE sync_items SET nextRetryAt = NULL WHERE entityType = :type AND state = :retrying " +
            "AND lastError = :reason",
    )
    abstract suspend fun releaseDeferred(type: String, retrying: String, reason: String): Int

    @Query(
        "SELECT COUNT(CASE WHEN cloudRef IS NOT NULL THEN 1 END) AS synced, COUNT(*) AS total " +
            "FROM documents WHERE deletedAt IS NULL",
    )
    abstract fun observeFileCounts(): Flow<FileCountsRow>
}
