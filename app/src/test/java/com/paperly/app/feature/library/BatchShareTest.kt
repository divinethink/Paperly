package com.paperly.app.feature.library

import com.paperly.app.domain.document.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchShareTest {
    private fun doc(title: String, type: String = "scanned-pdf") = Document(title, title, type, 1L, 0L)

    @Test fun uniqueNamesKeepTheDocumentNameAndTypeExtension() {
        val names = shareFileNames(listOf(doc("Scan A"), doc("Book", "epub"), doc("Scan B")))
        assertEquals(listOf("Scan A.pdf", "Book.epub", "Scan B.pdf"), names)
    }

    @Test fun repeatedNamesGetANumberBeforeTheExtension() {
        val names = shareFileNames(listOf(doc("Scan"), doc("Scan"), doc("Scan")))
        assertEquals(listOf("Scan.pdf", "Scan (2).pdf", "Scan (3).pdf"), names)
    }

    @Test fun aNumberedNameNeverCollidesWithARealOne() {
        val names = shareFileNames(listOf(doc("Scan"), doc("Scan (2)"), doc("Scan")))
        assertEquals(listOf("Scan.pdf", "Scan (2).pdf", "Scan (3).pdf"), names)
    }

    @Test fun unsafeCharactersAreReplaced() {
        assertEquals(listOf("a_b.pdf"), shareFileNames(listOf(doc("a/b"))))
    }
}
