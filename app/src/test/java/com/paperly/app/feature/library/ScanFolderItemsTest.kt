package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import com.paperly.app.domain.folder.Folder
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFolderItemsTest {
    private fun doc(id: String, folder: String? = null, type: String = "scanned-pdf") =
        Document(id, id, type, 1L, 0L, folderId = folder)

    private val folders = listOf(Folder("f1", "Bills"), Folder("f2", "Study"))

    @Test fun countsOnlyDocumentsOfTheTabTypeInsideEachFolder() {
        val docs = listOf(doc("a", "f1"), doc("b", "f1"), doc("c", "f2"), doc("d", "f1", type = "pdf"), doc("e"))
        val items = scanFolderItems(folders, docs, "scanned-pdf")
        assertEquals(listOf("Bills" to 2, "Study" to 1), items.map { it.name to it.count })
    }

    @Test fun anEmptyFolderStillShowsWithZero() {
        assertEquals(listOf(0, 0), scanFolderItems(folders, emptyList(), "scanned-pdf").map { it.count })
    }

    @Test fun aFolderCountIncludesItsSubfoldersAndRowsAreOnlyTheDirectChildren() {
        val nested = folders + Folder("f3", "2026", parentId = "f1")
        val docs = listOf(doc("a", "f1"), doc("b", "f3"), doc("c", "f3"), doc("d", "f2"))
        val top = scanFolderItems(nested, docs, "scanned-pdf")
        assertEquals(listOf("Bills" to 3, "Study" to 1), top.map { it.name to it.count })
        val inside = scanFolderItems(nested, docs, "scanned-pdf", "f1")
        assertEquals(listOf("2026" to 2), inside.map { it.name to it.count })
    }

    @Test fun rootListHidesDocumentsThatAreInAFolder() {
        val docs = listOf(doc("a", "f1"), doc("b"), doc("c", "gone"))
        assertEquals(listOf("b"), docs.withoutFolder().map { it.id })
    }

    @Test fun stackLayersGrowWithPagesUpToTwo() {
        assertEquals(listOf(0, 0, 0, 1, 2, 2), listOf(null, 0, 1, 2, 3, 40).map { scanStackLayers(it) })
    }
}
