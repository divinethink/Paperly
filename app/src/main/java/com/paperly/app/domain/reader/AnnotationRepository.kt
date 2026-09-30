package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Runtime-validated (stored as a string key): unknown keys from a newer/older schema are skipped, never crash. */
enum class AnnotationType(val key: String) {
    HIGHLIGHT("highlight"),
    UNDERLINE("underline"),
    STRIKETHROUGH("strikethrough"),
    NOTE("note"),
    ;

    companion object {
        fun fromKey(key: String): AnnotationType? = entries.firstOrNull { it.key == key }
    }
}

/** [page] is a zero-based page index; [rect] is in page fractions (0..1), origin top-left. */
data class Annotation(
    val id: String,
    val page: Int,
    val type: AnnotationType,
    val rect: MatchRect,
    val noteText: String?,
)

interface AnnotationRepository {
    fun observe(documentId: String): Flow<List<Annotation>>

    suspend fun add(documentId: String, page: Int, type: AnnotationType, rect: MatchRect, noteText: String?)

    suspend fun update(id: String, type: AnnotationType, noteText: String?)

    suspend fun delete(id: String)
}
