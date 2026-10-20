package com.paperly.app.domain.document

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    /** Source format when this EPUB was made by the converter (e.g. "docx"); null for imported files. */
    val convertedFrom: String? = null,
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

    /** Ids of documents read to the end (saved progress 100%); emits on every change. */
    val finishedIds: Flow<Set<String>> get() = flowOf(emptySet())
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

    /** Quick view -> "Save to Library": stores an app-private PDF/EPUB (file:// URI; caller keeps it) as [type]. */
    suspend fun importQuick(fileUri: String, title: String, type: String): ImportResult

    /**
     * Converter: stores an already validated EPUB (app-private file:// URI) as an "epub" document titled [title],
     * recording [convertedFrom] (e.g. "docx"). Never silently duplicates. The temp file is deleted afterwards.
     */
    suspend fun importConverted(epubUri: String, title: String, convertedFrom: String): ImportResult
}
