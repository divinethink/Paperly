package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** The type + color + thickness the user picked from the Annotate (pencil) strip; applied to every new annotation. */
data class AnnotateStyle(
    val type: AnnotationType = AnnotationType.UNDERLINE,
    val color: AnnotationColor = AnnotationColor.RED,
    val stroke: AnnotationStroke = AnnotationStroke.THIN,
) {
    companion object {
        /** First look of a tool nobody has styled yet: amber highlight, red lines. */
        fun defaultFor(type: AnnotationType) = AnnotateStyle(type, AnnotationColor.defaultFor(type))
    }
}

/** The look remembered for [type] (its own colour and thickness), or the default one. */
fun Map<AnnotationType, AnnotateStyle>.lookFor(type: AnnotationType): AnnotateStyle =
    this[type] ?: AnnotateStyle.defaultFor(type)

interface AnnotateStyleStore {
    /** The last style saved: the current tool with its colour and thickness. */
    val style: Flow<AnnotateStyle>

    /** Each tool's own remembered colour and thickness (Highlight, Underline, Squiggly, Strikethrough). */
    val tools: Flow<Map<AnnotationType, AnnotateStyle>>

    /** Makes [style] current and remembers its colour and thickness for its tool. */
    suspend fun save(style: AnnotateStyle)
}
