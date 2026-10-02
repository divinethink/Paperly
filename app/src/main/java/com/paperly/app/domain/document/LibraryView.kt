package com.paperly.app.domain.document

import kotlinx.coroutines.flow.Flow

/** Library sort key; DEFAULT keeps the stored order (last changed first). Persisted by [key] (unknown -> DEFAULT). */
enum class LibrarySort(val key: String) {
    DEFAULT("default"),
    NAME("name"),
    ADDED("added"),
    OPENED("opened"),
    SIZE("size"),
    ;

    companion object {
        fun fromKey(key: String?): LibrarySort = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Library type filter; [docType] is the stored `Document.type`. Persisted by [key] (unknown -> none). */
enum class LibraryType(val key: String, val docType: String) {
    PDF("pdf", "pdf"),
    EPUB("epub", "epub"),
    SCAN("scan", "scanned-pdf"),
    ;

    companion object {
        fun fromKey(key: String?): LibraryType? = entries.firstOrNull { it.key == key }
    }
}

data class LibraryView(
    val sort: LibrarySort = LibrarySort.DEFAULT,
    val ascending: Boolean = false,
    val type: LibraryType? = null,
)

interface LibraryViewStore {
    val view: Flow<LibraryView>

    suspend fun save(view: LibraryView)
}
