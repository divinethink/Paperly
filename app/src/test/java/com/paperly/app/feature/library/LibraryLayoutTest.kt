package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.LibraryLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutTest {
    @Test
    fun unknownOrMissingKeyFallsBackToGrid() {
        assertEquals(LibraryLayout.GRID, LibraryLayout.fromKey(null))
        assertEquals(LibraryLayout.GRID, LibraryLayout.fromKey("tiles"))
        assertEquals(LibraryLayout.LIST, LibraryLayout.fromKey("list"))
    }

    @Test
    fun finishedFilterKeepsSortOrderLikeAll() {
        val docs = listOf(Document("a", "B", "epub", 1L, 0L), Document("b", "A", "epub", 1L, 0L))
        val all = docs.filterFor(LibraryFilter.All)
        assertEquals(all, docs.filterFor(LibraryFilter.Finished))
    }
}
