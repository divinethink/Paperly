package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Architecture §২.১ Annotation. `locator` = PDF page-index string; rect = fractions (0..1) of the page,
 * origin top-left (zoom-independent). `type` is a runtime-validated string. CASCADE: row goes with its document.
 */
@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class AnnotationEntity(
    @PrimaryKey val annotationId: String,
    val documentId: String,
    val locator: String,
    val type: String,
    val color: String? = null,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val noteText: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** v5: JSON `[[l,t,r,b],…]` page fractions (multi-part); null = single part (the four rect columns). */
    val rects: String? = null,
    /** v11: line thickness key (`thin`/`medium`/`thick`); null = medium (all rows made before v11). */
    val stroke: String? = null,
)

/** The columns that change when a mark is moved or resized, plus its id (partial row for [AnnotationDao.setRects]). */
data class AnnotationRectsUpdate(
    val annotationId: String,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val rects: String?,
    val updatedAt: Long,
)

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :id ORDER BY createdAt")
    fun observeForDocument(id: String): Flow<List<AnnotationEntity>>

    /** IGNORE: a retried insert with the same id is a no-op (idempotent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AnnotationEntity): Long

    @Query(
        "UPDATE annotations SET type = :type, color = :color, noteText = :note, updatedAt = :now " +
            "WHERE annotationId = :id",
    )
    suspend fun update(id: String, type: String, color: String?, note: String?, now: Long): Int

    @Query("UPDATE annotations SET stroke = :stroke WHERE annotationId = :id")
    suspend fun setStroke(id: String, stroke: String?): Int

    /** A mark was moved / resized: only its place changes (the row is matched by `annotationId`). */
    @Update(entity = AnnotationEntity::class)
    suspend fun setRects(update: AnnotationRectsUpdate): Int

    @Query("DELETE FROM annotations WHERE annotationId = :id")
    suspend fun delete(id: String): Int
}
