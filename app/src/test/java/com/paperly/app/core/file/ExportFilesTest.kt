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
    fun pageImageNamesAreNumberedOnlyForSeveralPages() {
        assertEquals("Scan.jpg", ExportFiles.imageName("Scan", 0, 1))
        assertEquals("Scan-001.jpg", ExportFiles.imageName("Scan", 0, 3))
        assertEquals("Scan-012.jpg", ExportFiles.imageName("Scan", 11, 12))
    }

    @Test
    fun baseNameHasNoExtension() {
        assertEquals("Scan 1", ExportFiles.baseName(" Scan 1 "))
    }

    @Test
    fun longNamesAreCapped() {
        assertTrue(ExportFiles.fileName("x".repeat(500), "pdf").length <= 84)
    }
}
