package com.paperly.app.data.backup

import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.core.database.BookmarkEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import javax.inject.Inject
import javax.inject.Singleton

data class BackupReadingState(
    val documentId: String,
    val locator: String,
    val progressPercent: Float,
    val lastOpenedAt: Long,
)

data class BackupBookmark(
    val bookmarkId: String,
    val documentId: String,
    val locator: String,
    val title: String?,
    val createdAt: Long,
)

data class BackupAnnotation(
    val annotationId: String,
    val documentId: String,
    val locator: String,
    val type: String,
    val color: String?,
    val rectLeft: Float,
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val noteText: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Optional manifest extras: reading progress, bookmarks, annotations. Absent in old archives = empty. */
data class BackupExtras(
    val readingState: List<BackupReadingState> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val annotations: List<BackupAnnotation> = emptyList(),
) {
    companion object {
        val EMPTY = BackupExtras()
    }
}

/** data/ boundary for the reader extras: collect for export, insert-ignore for restore. */
@Singleton
class BackupExtrasStore @Inject constructor(db: PaperlyDatabase) {
    private val dao = db.backupExtrasDao()

    /** Only rows of [documentIds]; non-finite numbers are skipped (they cannot be encoded as JSON). */
    suspend fun collect(documentIds: Set<String>): BackupExtras = BackupExtras(
        readingState = dao.getAllReadingState()
            .filter { it.documentId in documentIds && it.progressPercent.isFinite() }
            .map { BackupReadingState(it.documentId, it.locator, it.progressPercent, it.lastOpenedAt) },
        bookmarks = dao.getAllBookmarks()
            .filter { it.documentId in documentIds }
            .map { BackupBookmark(it.bookmarkId, it.documentId, it.locator, it.title, it.createdAt) },
        annotations = dao.getAllAnnotations()
            .filter { it.documentId in documentIds && it.hasFiniteRect() }
            .map { it.toBackup() },
    )

    /** Call only for a document that was just restored (its row exists). Idempotent; never overwrites. */
    suspend fun restore(documentId: String, extras: BackupExtras) {
        val state = extras.readingState.firstOrNull { it.documentId == documentId }
            ?.let { ReadingStateEntity(it.documentId, it.locator, it.progressPercent, it.lastOpenedAt) }
        val bookmarks = extras.bookmarks.filter { it.documentId == documentId }
            .map { BookmarkEntity(it.bookmarkId, it.documentId, it.locator, it.title, it.createdAt) }
        val annotations = extras.annotations.filter { it.documentId == documentId }.map { it.toEntity() }
        if (state != null || bookmarks.isNotEmpty() || annotations.isNotEmpty()) {
            dao.restoreFor(state, bookmarks, annotations)
        }
    }
}

private fun AnnotationEntity.hasFiniteRect() =
    listOf(rectLeft, rectTop, rectRight, rectBottom).all { it.isFinite() }

private fun AnnotationEntity.toBackup() = BackupAnnotation(
    annotationId = annotationId,
    documentId = documentId,
    locator = locator,
    type = type,
    color = color,
    rectLeft = rectLeft,
    rectTop = rectTop,
    rectRight = rectRight,
    rectBottom = rectBottom,
    noteText = noteText,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupAnnotation.toEntity() = AnnotationEntity(
    annotationId = annotationId,
    documentId = documentId,
    locator = locator,
    type = type,
    color = color,
    rectLeft = rectLeft,
    rectTop = rectTop,
    rectRight = rectRight,
    rectBottom = rectBottom,
    noteText = noteText,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
