package com.paperly.app.core.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFilesTest {
    @Test
    fun unsafeCharsReplacedAndExtensionAdded() {
        assertEquals("a_b_c.pdf", ExportFiles.fileName("a/b:c", "pdf"))
        assertEquals("Book.epub", ExportFiles.fileName("  Book ", "epub"))
    }

    @Test
    fun blankTitleFallsBackAndScanIsPdf() {
        assertEquals("document.pdf", ExportFiles.fileName("   ", "scanned-pdf"))
        assertEquals("application/pdf", ExportFiles.mimeType("scanned-pdf"))
    }

    @Test
    fun longNamesAreCapped() {
        assertTrue(ExportFiles.fileName("x".repeat(500), "pdf").length <= 84)
    }
}
