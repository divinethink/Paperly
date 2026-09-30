package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderThemeTest {
    @Test
    fun everyThemeRoundTripsThroughItsKey() {
        ReaderTheme.entries.forEach { assertEquals(it, ReaderTheme.fromKey(it.key)) }
    }

    @Test
    fun unknownOrMissingKeyFallsBackToAuto() {
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromKey(null))
        assertEquals(ReaderTheme.AUTO, ReaderTheme.fromKey("neon"))
    }
}
