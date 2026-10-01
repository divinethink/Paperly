package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** EPUB annotation; [locatorJson] = Readium Locator (with selected text) in the shared `annotations` table. */
data class EpubAnnotation(
    val id: String,
    val locatorJson: String,
    val type: AnnotationType,
    val color: AnnotationColor?,
    val noteText: String?,
)

interface EpubAnnotationRepository {
    fun observe(documentId: String): Flow<List<EpubAnnotation>>

    suspend fun add(documentId: String, locatorJson: String, content: AnnotationContent)

    suspend fun update(id: String, content: AnnotationContent)

    suspend fun delete(id: String)
}
