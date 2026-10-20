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

/** How PDF/EPUB documents are laid out: small 3-column covers or one row per document. Persisted by [key]. */
enum class LibraryLayout(val key: String) {
    GRID("grid"),
    LIST("list"),
    ;

    companion object {
        fun fromKey(key: String?): LibraryLayout = entries.firstOrNull { it.key == key } ?: GRID
    }
}

data class LibraryView(
    val sort: LibrarySort = LibrarySort.DEFAULT,
    val ascending: Boolean = false,
    val type: LibraryType? = null,
    val layout: LibraryLayout = LibraryLayout.GRID,
)

interface LibraryViewStore {
    val view: Flow<LibraryView>

    suspend fun save(view: LibraryView)
}
