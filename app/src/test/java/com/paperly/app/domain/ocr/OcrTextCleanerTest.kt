package com.paperly.app.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextCleanerTest {
    @Test
    fun removesStrayLatinFragmentsOnBengaliPage() {
        assertEquals("আজ বাতাস নেই।", OcrTextCleaner.clean("আজ বাতাস AYA নেই।"))
        assertEquals("আগে\n\nপরে", OcrTextCleaner.clean("আগে\n\nfag\nপরে"))
    }

    @Test
    fun keepsRealEnglishWordsOnBengaliPage() {
        assertEquals("মিলছে না Something is wrong", OcrTextCleaner.clean("মিলছে না Something is wrong"))
    }

    @Test
    fun leavesEnglishOnlyPageUntouched() {
        assertEquals("fag xyz", OcrTextCleaner.clean("fag xyz"))
    }
}
