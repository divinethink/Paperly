package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** The type + color the user picked from the Annotate (pencil) strip; applied to every new annotation. */
data class AnnotateStyle(
    val type: AnnotationType = AnnotationType.UNDERLINE,
    val color: AnnotationColor = AnnotationColor.RED,
)

interface AnnotateStyleStore {
    val style: Flow<AnnotateStyle>

    suspend fun save(style: AnnotateStyle)
}
