package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EpubFontTest {
    @Test
    fun unknownOrMissingKeyFallsBackToSerif() {
        assertEquals(EpubFont.SERIF, EpubFont.fromKey(null))
        assertEquals(EpubFont.SERIF, EpubFont.fromKey("comic_sans"))
    }

    @Test
    fun savedKeysStillResolve() {
        assertEquals(EpubFont.SANS, EpubFont.fromKey("sans"))
        assertEquals(EpubFont.INTER, EpubFont.fromKey("inter"))
    }

    @Test
    fun bundledFontsHaveSpaceFreeFamilyAndFile() {
        EpubFont.entries.filter { it.assetFile != null }.forEach {
            assertNotNull(it.assetFile)
            assertEquals(it.family.replace(" ", ""), it.family)
        }
    }
}
