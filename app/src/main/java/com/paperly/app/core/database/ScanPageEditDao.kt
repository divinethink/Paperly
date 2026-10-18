package com.paperly.app.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * S2: editor state of one page of a scanned document. [srcFile] = file name inside `files/scans/<documentId>/`
 * (the un-edited source JPEG), [cornersJson] = `[x0,y0,…,x3,y3]` as source-image fractions (null = full image),
 * [filter] = ScanFilter name, [brightness]/[contrast] = manual adjustment (0 = none; added in v13).
 * A row exists only once the page was edited. CASCADE: it goes with its document.
 */
@Entity(
    tableName = "scan_page_edit",
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
data class ScanPageEditEntity(
    val documentId: String,
    val pageIndex: Int,
    val srcFile: String,
    val rotation: Int,
    val cornersJson: String?,
    val filter: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val brightness: Int = 0,
    @ColumnInfo(defaultValue = "0") val contrast: Int = 0,
)

@Dao
interface ScanPageEditDao {
    @Query("SELECT * FROM scan_page_edit WHERE documentId = :documentId ORDER BY pageIndex")
    suspend fun getForDocument(documentId: String): List<ScanPageEditEntity>

    /** Locators of the document's annotations (PDF = page index as text): which pages carry highlights. */
    @Query("SELECT DISTINCT locator FROM annotations WHERE documentId = :documentId")
    suspend fun getAnnotationLocators(documentId: String): List<String>

    /** REPLACE on the composite key = idempotent save of the same page. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ScanPageEditEntity>)
}
