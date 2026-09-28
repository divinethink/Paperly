package com.paperly.app.domain.document

import kotlinx.coroutines.flow.Flow

/** UI-facing model: features never touch the Room entity directly. */
data class Document(
    val id: String,
    val title: String,
    val type: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

sealed interface ImportResult {
    data class Success(val documentId: String) : ImportResult
    data object UnsupportedType : ImportResult
    data object Failed : ImportResult
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    suspend fun getDocument(id: String): Document?

    /** [sourceUri] is a content:// URI string from the system picker. */
    suspend fun importDocument(sourceUri: String): ImportResult
}
