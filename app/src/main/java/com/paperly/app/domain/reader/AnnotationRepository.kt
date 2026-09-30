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

/** Runtime-validated palette key (stored in the `color` column); an unknown key reads as null = the type's default. */
enum class AnnotationColor(val key: String) {
    AMBER("amber"),
    RED("red"),
    GREEN("green"),
    BLUE("blue"),
    PURPLE("purple"),
    BLACK("black"),
    ;

    companion object {
        fun fromKey(key: String?): AnnotationColor? = entries.firstOrNull { it.key == key }

        /** Colour used when none was picked: amber highlight (as before), red line for underline/strikethrough. */
        fun defaultFor(type: AnnotationType): AnnotationColor = if (type == AnnotationType.HIGHLIGHT) AMBER else RED
    }
}

/** What the user chooses for one annotation. [color] null = default for [type]; ignored for notes. */
data class AnnotationContent(val type: AnnotationType, val color: AnnotationColor?, val noteText: String?)

/** [page] is a zero-based page index; [rect] is in page fractions (0..1), origin top-left. */
data class Annotation(
    val id: String,
    val page: Int,
    val type: AnnotationType,
    val rect: MatchRect,
    val noteText: String?,
    val color: AnnotationColor? = null,
)

interface AnnotationRepository {
    fun observe(documentId: String): Flow<List<Annotation>>

    suspend fun add(documentId: String, page: Int, rect: MatchRect, content: AnnotationContent)

    suspend fun update(id: String, content: AnnotationContent)

    suspend fun delete(id: String)
}
