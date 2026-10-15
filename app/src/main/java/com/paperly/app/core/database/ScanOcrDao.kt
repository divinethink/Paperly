package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S3: recognised text of one page of a scanned document. A row (even with empty [text] = blank page) means the page
 * was read with [engineVersion]; no row = not read yet. Derived data: it can always be re-created from the PDF, so it
 * is not part of Backup/Sync. CASCADE: it goes with its document.
 */
@Entity(
    tableName = "scan_ocr",
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
data class ScanOcrEntity(
    val documentId: String,
    val pageIndex: Int,
    val text: String,
    val lang: String,
    val engineVersion: Int,
    val updatedAt: Long,
)

@Dao
interface ScanOcrDao {
    @Query("SELECT * FROM scan_ocr WHERE documentId = :documentId ORDER BY pageIndex")
    suspend fun getForDocument(documentId: String): List<ScanOcrEntity>

    @Query("SELECT pageIndex FROM scan_ocr WHERE documentId = :documentId AND engineVersion = :engineVersion")
    suspend fun donePages(documentId: String, engineVersion: Int): List<Int>

    /** REPLACE on the composite key = idempotent save of the same page. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScanOcrEntity)

    @Query("DELETE FROM scan_ocr WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String): Int

    /** [pattern] must already be [com.paperly.app.domain.scanner.escapeLike]-escaped. Complete set, no LIMIT. */
    @Query("SELECT DISTINCT documentId FROM scan_ocr WHERE text LIKE '%' || :pattern || '%' ESCAPE '\\'")
    fun observeMatching(pattern: String): Flow<List<String>>
}
