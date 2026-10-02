package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.core.model.syncIdOf

/**
 * P8-C: the sync queue. Deliberately NO foreign key to documents — a DELETE item must outlive the document row.
 * `updatedAt` doubles as the row's version: every re-enqueue strictly increases it, and the worker only
 * completes/changes an item whose version it still holds, so a change made during a run is never lost.
 */
@Entity(tableName = "sync_items")
data class SyncItemEntity(
    @PrimaryKey val syncId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val state: String,
    val attempts: Int = 0,
    val nextRetryAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
abstract class SyncItemDao {
    @Query(
        "UPDATE sync_items SET operation = :operation, state = :queued, attempts = 0, nextRetryAt = NULL, " +
            "lastError = NULL, updatedAt = MAX(:now, updatedAt + 1) WHERE syncId = :syncId",
    )
    abstract suspend fun requeue(syncId: String, operation: String, queued: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: SyncItemEntity): Long

    /** Latest change wins: an existing item is reset to QUEUED with the new operation (coalescing), else inserted. */
    @Transaction
    open suspend fun enqueue(entityType: String, entityId: String, operation: String, now: Long) {
        val id = syncIdOf(entityType, entityId)
        if (requeue(id, operation, SyncItemState.QUEUED, now) == 0) {
            insertIgnore(
                SyncItemEntity(
                    syncId = id,
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    state = SyncItemState.QUEUED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    @Query("SELECT documentId FROM documents")
    abstract suspend fun allDocumentIds(): List<String>

    /** Every document (trashed ones too), one PUT each. Whole table on purpose: a truncated set would be wrong. */
    @Transaction
    open suspend fun enqueueAllDocuments(now: Long) {
        allDocumentIds().forEach { enqueue(SyncEntityType.DOCUMENT, it, SyncOperation.PUT, now) }
    }

    /** Due = not FAILED and its retry time (if any) has come. RUNNING is included so an interrupted run self-heals. */
    @Query(
        "SELECT * FROM sync_items WHERE state <> :failed AND (nextRetryAt IS NULL OR nextRetryAt <= :now) " +
            "ORDER BY createdAt LIMIT :limit",
    )
    abstract suspend fun getDue(failed: String, now: Long, limit: Int): List<SyncItemEntity>

    /** Only succeeds for the exact version the worker read. */
    @Query(
        "UPDATE sync_items SET state = :running WHERE syncId = :syncId AND updatedAt = :version AND state <> :failed",
    )
    abstract suspend fun claim(syncId: String, version: Long, running: String, failed: String): Int

    @Query("DELETE FROM sync_items WHERE syncId = :syncId AND updatedAt = :version")
    abstract suspend fun complete(syncId: String, version: Long): Int

    /** Retry/failed bookkeeping; does not bump the version, and is a no-op if the item changed meanwhile. */
    @Query(
        "UPDATE sync_items SET state = :state, attempts = :attempts, nextRetryAt = :nextRetryAt, " +
            "lastError = :lastError WHERE syncId = :syncId AND updatedAt = :version",
    )
    abstract suspend fun markState(
        syncId: String,
        version: Long,
        state: String,
        attempts: Int,
        nextRetryAt: Long?,
        lastError: String?,
    ): Int

    /** Items still waiting to be synced (aggregate, no LIMIT). */
    @Query("SELECT COUNT(*) FROM sync_items WHERE state <> :failed")
    abstract suspend fun countWaiting(failed: String): Int
}
