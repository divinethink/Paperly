package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTimeTest {
    @Test
    fun countsWholeSeconds() = assertEquals(90L, countedSeconds(90_999L))

    @Test
    fun capsAnIdleSpanAtThirtyMinutes() = assertEquals(1_800L, countedSeconds(5L * 60 * 60 * 1000))

    @Test
    fun negativeSpanCountsAsZero() = assertEquals(0L, countedSeconds(-5_000L))
}
