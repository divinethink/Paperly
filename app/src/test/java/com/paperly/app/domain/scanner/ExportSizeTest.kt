package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSizeTest {
    @Test
    fun `actual keeps the usual jpg export settings`() {
        assertEquals(1600, ExportSize.ACTUAL.widthPx)
        assertEquals(90, ExportSize.ACTUAL.jpegQuality)
    }

    @Test
    fun `each smaller choice has fewer pixels and lower quality`() {
        val sizes = ExportSize.entries
        sizes.zipWithNext().forEach { (bigger, smaller) ->
            assertTrue(smaller.widthPx < bigger.widthPx)
            assertTrue(smaller.jpegQuality < bigger.jpegQuality)
        }
    }
}
