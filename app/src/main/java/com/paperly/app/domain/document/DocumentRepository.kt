package com.paperly.app.domain.document

import kotlinx.coroutines.flow.Flow

/** UI-facing model: features never touch the Room entity directly. */
data class Document(
    val id: String,
    val title: String,
    val type: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long? = null,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
)

const val MAX_TITLE_LENGTH = 200

sealed interface ImportResult {
    data class Success(val documentId: String) : ImportResult

    /** Same content (checksum) already in the Library; nothing was imported. */
    data class Duplicate(val existingId: String, val existingTitle: String) : ImportResult
    data object UnsupportedType : ImportResult
    data object Failed : ImportResult
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    suspend fun getDocument(id: String): Document?

    /** Metadata-only rename (the file itself is never touched). False if blank or the document is gone. */
    suspend fun renameDocument(id: String, newTitle: String): Boolean

    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Stamps lastOpenedAt (drives "Recent"). */
    suspend fun markOpened(id: String)

    /**
     * [sourceUri] is a content:// URI string from the system picker.
     * [allowDuplicate] = true is the explicit "Keep Both" choice; default never silently duplicates.
     */
    suspend fun importDocument(sourceUri: String, allowDuplicate: Boolean = false): ImportResult

    /** P4: stores a scanner-produced PDF as a "scanned-pdf" document titled [title]. Never silently duplicates. */
    suspend fun importScan(pdfUri: String, title: String): ImportResult
}
