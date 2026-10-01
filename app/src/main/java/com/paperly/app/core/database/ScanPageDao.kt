package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * P4-B: optional per-page title/note of a scanned document. A row exists only once the owner edits a page
 * (no row = untitled, no note). CASCADE: the row goes with its document.
 */
@Entity(
    tableName = "scan_pages",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScanPageEntity(
    val documentId: String,
    val pageIndex: Int,
    val title: String? = null,
    val note: String? = null,
    val updatedAt: Long,
)

@Dao
interface ScanPageDao {
    @Query("SELECT * FROM scan_pages WHERE documentId = :documentId AND pageIndex = :pageIndex")
    fun observe(documentId: String, pageIndex: Int): Flow<ScanPageEntity?>

    /** REPLACE on the composite key = idempotent save of the same page. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScanPageEntity)

    @Query("DELETE FROM scan_pages WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun delete(documentId: String, pageIndex: Int)
}
