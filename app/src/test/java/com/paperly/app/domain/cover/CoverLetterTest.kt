package com.paperly.app.domain.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLetterTest {
    @Test fun latinUppercased() = assertEquals("A", CoverLetter.letter("  alpha"))
    @Test fun bengaliFirstCodePoint() = assertEquals("ব", CoverLetter.letter("বাংলা"))
    @Test fun blankFallsBack() = assertEquals("?", CoverLetter.letter("   "))
    @Test fun indexInRangeAndStable() {
        val i = CoverLetter.colorIndex("Some Title", 8)
        assertTrue(i in 0..7)
        assertEquals(i, CoverLetter.colorIndex("Some Title ", 8))
    }
}
