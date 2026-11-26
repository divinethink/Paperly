package com.paperly.app.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsePageNumberTest {
    @Test
    fun pagesInsideTheDocumentAreAccepted() {
        assertEquals(1, parsePageNumber("1", 20))
        assertEquals(20, parsePageNumber(" 20 ", 20))
    }

    @Test
    fun zeroTooBigAndNonNumbersAreRejected() {
        assertNull(parsePageNumber("0", 20))
        assertNull(parsePageNumber("21", 20))
        assertNull(parsePageNumber("", 20))
        assertNull(parsePageNumber("ab", 20))
    }
}
