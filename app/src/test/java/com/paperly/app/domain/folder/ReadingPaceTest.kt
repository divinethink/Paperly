package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPaceTest {
    @Test
    fun noEstimateWithoutData() {
        assertNull(ReadingPace().minutesLeft(0.5f))
    }

    @Test
    fun estimatesFromForwardSteps() {
        val pace = ReadingPace()
        // 10 steps of 1% every 6 s: 10% in 60 s -> remaining 80% = 480 s = 8 min.
        for (i in 0..10) pace.record(0.01f * i, 6_000L * i + 1_000L)
        assertEquals(8, pace.minutesLeft(0.2f))
    }

    @Test
    fun jumpsAndIdleGapsAreIgnored() {
        val pace = ReadingPace()
        pace.record(0.0f, 1_000L)
        pace.record(0.5f, 10_000L) // jump, not reading
        pace.record(0.51f, 900_000L) // long idle gap
        assertNull(pace.minutesLeft(0.51f))
    }

    @Test
    fun backwardMovesAddNothing() {
        val pace = ReadingPace()
        pace.record(0.5f, 1_000L)
        pace.record(0.45f, 11_000L)
        assertNull(pace.minutesLeft(0.45f))
    }
}
