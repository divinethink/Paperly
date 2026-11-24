package com.paperly.app.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun knownKeysRoundTrip() {
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.fromKey(it.key)) }
    }

    @Test
    fun missingOrUnknownFollowsThePhone() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey("sepia"))
    }
}
