package com.paperly.app.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPaletteTest {
    @Test fun everyKeyRoundTrips() {
        AppPalette.entries.forEach { assertEquals(it, AppPalette.fromKey(it.key)) }
    }

    @Test fun missingOrUnknownKeyIsTheBrandPalette() {
        assertEquals(AppPalette.PAPERLY, AppPalette.fromKey(null))
        assertEquals(AppPalette.PAPERLY, AppPalette.fromKey("treasury"))
    }

    @Test fun keysAreUnique() {
        assertEquals(AppPalette.entries.size, AppPalette.entries.map { it.key }.toSet().size)
    }

    @Test fun brandPaletteFollowsTheModeOthersBringTheirOwnTone() {
        assertTrue(AppPalette.PAPERLY.isDark(true))
        assertFalse(AppPalette.PAPERLY.isDark(false))
        assertFalse(AppPalette.MEADOW.isDark(true))
        assertFalse(AppPalette.GLACIER.isDark(true))
        assertTrue(AppPalette.NEON_NIGHT.isDark(false))
    }
}
