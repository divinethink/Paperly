package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.paperly.app.core.model.SYNC_ERROR_CONFLICT
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.core.model.syncIdOf
import kotlinx.coroutines.flow.Flow

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

/**
 * P8-E1: the cloud version (`updatedAt`) of a document's metadata that this device last wrote or read. Basis of
 * conflict detection. No FK on purpose (same reason as sync_items); rows are small and removable at any time.
 */
@Entity(tableName = "sync_base")
data class SyncBaseEntity(
    @PrimaryKey val documentId: String,
    val remoteUpdatedAt: Long,
)

data class QueueCountsRow(val pending: Int, val failed: Int, val conflicts: Int)

data class ConflictRow(val documentId: String, val title: String)

@Dao
@Suppress("TooManyFunctions") // one DAO per table: queue, retry and conflict bookkeeping
abstract class SyncItemDao {
    @Query("SELECT COUNT(*) FROM sync_items WHERE syncId = :syncId")
    abstract suspend fun countItem(syncId: String): Int

    @Query(
        "SELECT COUNT(CASE WHEN state <> '${SyncItemState.FAILED}' THEN 1 END) AS pending, " +
            "COUNT(CASE WHEN state = '${SyncItemState.FAILED}' AND IFNULL(lastError, '') <> '$SYNC_ERROR_CONFLICT' " +
            "THEN 1 END) AS failed, " +
            "COUNT(CASE WHEN state = '${SyncItemState.FAILED}' AND lastError = '$SYNC_ERROR_CONFLICT' THEN 1 END) " +
            "AS conflicts FROM sync_items",
    )
    abstract fun observeQueueCounts(): Flow<QueueCountsRow>

    @Query(
        "SELECT s.entityId AS documentId, d.title AS title FROM sync_items s " +
            "JOIN documents d ON d.documentId = s.entityId " +
            "WHERE s.entityType = '${SyncEntityType.DOCUMENT}' AND s.state = '${SyncItemState.FAILED}' " +
            "AND s.lastError = '$SYNC_ERROR_CONFLICT' ORDER BY d.title",
    )
    abstract fun observeConflicts(): Flow<List<ConflictRow>>

    /** Rejected items (not conflicts) go back to QUEUED with a fresh version. */
    @Query(
        "UPDATE sync_items SET state = '${SyncItemState.QUEUED}', attempts = 0, nextRetryAt = NULL, " +
            "lastError = NULL, updatedAt = MAX(:now, updatedAt + 1) WHERE state = '${SyncItemState.FAILED}' " +
            "AND IFNULL(lastError, '') <> '$SYNC_ERROR_CONFLICT'",
    )
    abstract suspend fun retryFailed(now: Long): Int

    /** Only a still-unchanged conflict item is dropped: an edit made meanwhile re-queued it and must stay. */
    @Query(
        "DELETE FROM sync_items WHERE syncId = :syncId AND state = '${SyncItemState.FAILED}' " +
            "AND lastError = '$SYNC_ERROR_CONFLICT'",
    )
    abstract suspend fun dropConflict(syncId: String): Int

    @Query("SELECT remoteUpdatedAt FROM sync_base WHERE documentId = :id")
    abstract suspend fun getBase(id: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setBase(entity: SyncBaseEntity)

    @Query("DELETE FROM sync_base WHERE documentId = :id")
    abstract suspend fun deleteBase(id: String): Int

    /** Another account signed in: versions seen in the old account's cloud mean nothing in the new one. */
    @Query("DELETE FROM sync_base")
    abstract suspend fun clearBases(): Int

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

    @Query("SELECT documentId FROM reading_state")
    abstract suspend fun allReadingIds(): List<String>

    /** Every stored reading position, one PUT each (new account / restore). Whole table on purpose. */
    @Transaction
    open suspend fun enqueueAllReading(now: Long) {
        allReadingIds().forEach { enqueue(SyncEntityType.READING, it, SyncOperation.PUT, now) }
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

    /** Transient failure: wait until [nextRetryAt]. No-op if the item changed meanwhile (version mismatch). */
    @Query(
        "UPDATE sync_items SET state = '${SyncItemState.RETRYING}', attempts = :attempts, " +
            "nextRetryAt = :nextRetryAt, lastError = :lastError WHERE syncId = :syncId AND updatedAt = :version",
    )
    abstract suspend fun markRetry(
        syncId: String,
        version: Long,
        attempts: Int,
        nextRetryAt: Long,
        lastError: String?,
    ): Int

    /** Rejected for good: stays FAILED until the entity is enqueued again. Same version guard as above. */
    @Query(
        "UPDATE sync_items SET state = '${SyncItemState.FAILED}', attempts = :attempts, nextRetryAt = NULL, " +
            "lastError = :lastError WHERE syncId = :syncId AND updatedAt = :version",
    )
    abstract suspend fun markFailed(syncId: String, version: Long, attempts: Int, lastError: String?): Int
}
