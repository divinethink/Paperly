package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Runtime-validated (stored as a string key): unknown keys from a newer/older schema are skipped, never crash. */
enum class AnnotationType(val key: String) {
    HIGHLIGHT("highlight"),
    UNDERLINE("underline"),
    SQUIGGLY("squiggly"),
    STRIKETHROUGH("strikethrough"),
    NOTE("note"),
    ;

    companion object {
        fun fromKey(key: String): AnnotationType? = entries.firstOrNull { it.key == key }
    }
}

private const val THIN_DP = 0.8f
private const val MEDIUM_DP = 1.5f
private const val THICK_DP = 2.5f

/**
 * Line thickness of an underline / squiggly / strikethrough (stored as a string key in `annotations.stroke`).
 * [dp] is the on-screen thickness; null in the database reads as [MEDIUM], the look before this existed.
 */
enum class AnnotationStroke(val key: String, val dp: Float) {
    THIN("thin", THIN_DP),
    MEDIUM("medium", MEDIUM_DP),
    THICK("thick", THICK_DP),
    ;

    companion object {
        fun fromKey(key: String?): AnnotationStroke? = entries.firstOrNull { it.key == key }

        /** Only line-type marks have a thickness; highlights and notes ignore it. */
        fun appliesTo(type: AnnotationType): Boolean =
            type == AnnotationType.UNDERLINE || type == AnnotationType.SQUIGGLY ||
                type == AnnotationType.STRIKETHROUGH
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
data class AnnotationContent(
    val type: AnnotationType,
    val color: AnnotationColor?,
    val noteText: String?,
    val stroke: AnnotationStroke? = null,
)

/** [page] is a zero-based page index; [rect] is in page fractions (0..1), origin top-left. */
data class Annotation(
    val id: String,
    val page: Int,
    val type: AnnotationType,
    val rect: MatchRect,
    val noteText: String?,
    val color: AnnotationColor? = null,
    /** All parts (page fractions); [rect] is their bounding box. Single-part = `listOf(rect)`. */
    val rects: List<MatchRect> = listOf(rect),
    val stroke: AnnotationStroke? = null,
)

interface AnnotationRepository {
    fun observe(documentId: String): Flow<List<Annotation>>

    /** [rects] non-empty = multi-part (≤50); [rect] is then replaced by their bounding box. */
    suspend fun add(
        documentId: String,
        page: Int,
        rect: MatchRect,
        content: AnnotationContent,
        rects: List<MatchRect> = emptyList(),
    )

    suspend fun update(id: String, content: AnnotationContent)

    suspend fun delete(id: String)
}
