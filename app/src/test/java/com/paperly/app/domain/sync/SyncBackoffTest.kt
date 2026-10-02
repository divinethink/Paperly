package com.paperly.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBackoffTest {
    @Test
    fun delayDoublesFromThirtySeconds() {
        assertEquals(30_000L, SyncBackoff.delayMs(1))
        assertEquals(60_000L, SyncBackoff.delayMs(2))
        assertEquals(120_000L, SyncBackoff.delayMs(3))
    }

    @Test
    fun delayIsCappedAndNeverOverflows() {
        val sixHours = 6L * 60 * 60 * 1000
        assertEquals(sixHours, SyncBackoff.delayMs(20))
        assertEquals(sixHours, SyncBackoff.delayMs(Int.MAX_VALUE))
        assertTrue(SyncBackoff.delayMs(0) > 0)
    }
}
