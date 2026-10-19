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

    @Test fun rootListHidesDocumentsThatAreInAFolder() {
        val docs = listOf(doc("a", "f1"), doc("b"), doc("c", "gone"))
        assertEquals(listOf("b"), docs.withoutFolder().map { it.id })
    }
}
