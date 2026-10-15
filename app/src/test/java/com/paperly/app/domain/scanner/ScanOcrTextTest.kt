package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanOcrTextTest {
    @Test
    fun escapeLikeMakesWildcardsLiteral() {
        assertEquals("100\\% a\\_b \\\\", escapeLike("100% a_b \\"))
        assertEquals("সাধারণ text", escapeLike("সাধারণ text"))
    }

    @Test
    fun joinKeepsPageOrderAndDropsBlankPages() {
        val pages = listOf(2 to " third ", 0 to "first", 1 to "  ")
        assertEquals("first\n\nthird", joinScanText(pages))
    }

    @Test
    fun joinIsNullWhenNothingWasRead() {
        assertNull(joinScanText(emptyList()))
        assertNull(joinScanText(listOf(0 to "", 1 to " \n")))
    }
}
