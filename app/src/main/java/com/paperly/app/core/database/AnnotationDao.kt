package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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
)

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :id ORDER BY createdAt")
    fun observeForDocument(id: String): Flow<List<AnnotationEntity>>

    /** IGNORE: a retried insert with the same id is a no-op (idempotent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AnnotationEntity): Long

    @Query("UPDATE annotations SET type = :type, noteText = :note, updatedAt = :now WHERE annotationId = :id")
    suspend fun update(id: String, type: String, note: String?, now: Long): Int

    @Query("DELETE FROM annotations WHERE annotationId = :id")
    suspend fun delete(id: String): Int
}
