package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.document.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private fun doc(
        id: String,
        title: String = id,
        type: String = "pdf",
        size: Long = 1L,
        added: Long = 0L,
        opened: Long? = null,
    ) = Document(id, title, type, size, added, lastOpenedAt = opened)

    private val docs = listOf(
        doc("a", title = "banana", size = 30L, added = 2L, opened = 5L),
        doc("b", title = "Apple", type = "epub", size = 10L, added = 3L),
        doc("c", title = "cherry", type = "scanned-pdf", size = 20L, added = 1L, opened = 9L),
    )

    private fun ids(view: LibraryView, kind: LibraryFilter = LibraryFilter.All) =
        docs.filterFor(kind, view = view).map { it.id }

    @Test
    fun defaultKeepsStoredOrder() {
        assertEquals(listOf("a", "b", "c"), ids(LibraryView()))
    }

    @Test
    fun nameAscendingIgnoresCase() {
        assertEquals(listOf("b", "a", "c"), ids(LibraryView(LibrarySort.NAME, ascending = true)))
    }

    @Test
    fun sizeDescendingPutsLargestFirst() {
        assertEquals(listOf("a", "c", "b"), ids(LibraryView(LibrarySort.SIZE)))
    }

    @Test
    fun openedDescendingPutsNeverOpenedLast() {
        assertEquals(listOf("c", "a", "b"), ids(LibraryView(LibrarySort.OPENED)))
    }

    @Test
    fun typeFilterKeepsOnlyThatType() {
        assertEquals(listOf("b"), ids(LibraryView(type = LibraryType.EPUB)))
        assertEquals(listOf("c"), ids(LibraryView(type = LibraryType.SCAN)))
    }

    @Test
    fun recentKeepsOwnOrderDespiteSort() {
        assertEquals(listOf("c", "a"), ids(LibraryView(LibrarySort.NAME, ascending = true), LibraryFilter.Recent))
    }

    @Test
    fun unknownKeysFallBack() {
        assertEquals(LibrarySort.DEFAULT, LibrarySort.fromKey("nope"))
        assertEquals(null, LibraryType.fromKey(null))
    }
}
