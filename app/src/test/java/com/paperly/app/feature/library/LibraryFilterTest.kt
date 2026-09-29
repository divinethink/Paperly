package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun doc(
        id: String,
        fav: Boolean = false,
        opened: Long? = null,
        title: String = id,
        folder: String? = null,
        tags: List<String> = emptyList(),
    ) = Document(id, title, "pdf", 1L, 0L, isFavorite = fav, lastOpenedAt = opened, folderId = folder, tags = tags)

    private val docs = listOf(
        doc("a", fav = true, opened = 10L),
        doc("b"),
        doc("c", opened = 30L),
        doc("d", fav = true, opened = 20L),
    )

    @Test
    fun allKeepsEverything() {
        assertEquals(listOf("a", "b", "c", "d"), docs.filterFor(LibraryFilter.All).map { it.id })
    }

    @Test
    fun favoritesKeepsOnlyFavorites() {
        assertEquals(listOf("a", "d"), docs.filterFor(LibraryFilter.Favorites).map { it.id })
    }

    @Test
    fun recentIsOpenedOnlyNewestFirst() {
        assertEquals(listOf("c", "d", "a"), docs.filterFor(LibraryFilter.Recent).map { it.id })
    }

    @Test
    fun searchMatchesTitleOrTagCaseInsensitively() {
        val list = listOf(
            doc("a", title = "Tax Return 2026"),
            doc("b", title = "Novel", tags = listOf("Fiction", "TAX")),
            doc("c", title = "Recipe"),
        )
        assertEquals(listOf("a", "b"), list.filterFor(LibraryFilter.All, query = " tax ").map { it.id })
    }

    @Test
    fun folderFilterKeepsOnlyThatFolder() {
        val list = listOf(doc("a", folder = "f1"), doc("b", folder = "f2"), doc("c"))
        assertEquals(listOf("a"), list.filterFor(LibraryFilter.All, folderId = "f1").map { it.id })
    }

    @Test
    fun searchAndFolderApplyBeforeFavoritesFilter() {
        val list = listOf(
            doc("a", fav = true, folder = "f1", title = "Bill"),
            doc("b", fav = true, folder = "f2", title = "Bill"),
            doc("c", folder = "f1", title = "Bill"),
        )
        val result = list.filterFor(LibraryFilter.Favorites, query = "bill", folderId = "f1")
        assertEquals(listOf("a"), result.map { it.id })
    }
}
