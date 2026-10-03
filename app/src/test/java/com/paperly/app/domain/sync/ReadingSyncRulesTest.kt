package com.paperly.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSyncRulesTest {
    private fun remote(updatedAt: Long) = ReadingMeta("d1", "5", 0.5f, updatedAt)

    @Test
    fun pushNeverMovesTheCloudBackwards() {
        assertTrue(ReadingPullDecision.shouldPush(null, 5L))
        assertTrue(ReadingPullDecision.shouldPush(5L, 6L))
        assertTrue(ReadingPullDecision.shouldPush(5L, 5L)) // same version: idempotent rewrite
        assertFalse(ReadingPullDecision.shouldPush(9L, 5L))
    }

    @Test
    fun pullTakesNewerOrMissingButNeverOverwritesUnsentOrNewerLocal() {
        assertEquals(ReadingPullAction.APPLY, ReadingPullDecision.decide(null, false, remote(5L)))
        assertEquals(ReadingPullAction.APPLY, ReadingPullDecision.decide(4L, false, remote(5L)))
        assertEquals(ReadingPullAction.SKIP, ReadingPullDecision.decide(5L, false, remote(5L)))
        assertEquals(ReadingPullAction.SKIP, ReadingPullDecision.decide(6L, false, remote(5L)))
        assertEquals(ReadingPullAction.SKIP, ReadingPullDecision.decide(4L, true, remote(5L)))
        assertEquals(ReadingPullAction.SKIP, ReadingPullDecision.decide(null, true, remote(5L)))
    }

    @Test
    fun debounceAllowsFirstChangeThenWaitsOneInterval() {
        assertTrue(ReadingDebounce.shouldEnqueue(null, 1_000L))
        assertFalse(ReadingDebounce.shouldEnqueue(1_000L, 1_000L + ReadingDebounce.INTERVAL_MS - 1))
        assertTrue(ReadingDebounce.shouldEnqueue(1_000L, 1_000L + ReadingDebounce.INTERVAL_MS))
        assertTrue(ReadingDebounce.shouldEnqueue(5_000L, 1_000L)) // clock went backwards
    }

    @Test
    fun throttleHoldsRapidChangesAndFlushReleasesTheLastOne() {
        var now = 0L
        val throttle = ReadingThrottle { now }
        assertTrue(throttle.onChange("d1")) // first goes through
        now = 1_000L
        assertFalse(throttle.onChange("d1")) // held back
        assertTrue(throttle.onFlush("d1")) // pause: the held change is released
        assertFalse(throttle.onFlush("d1")) // nothing left to release
        assertTrue(throttle.onChange("d2")) // other documents are independent
    }

    @Test
    fun flushWithoutAnyHeldChangeDoesNothing() {
        val throttle = ReadingThrottle { 0L }
        assertFalse(throttle.onFlush("d1"))
        assertTrue(throttle.onChange("d1"))
        assertFalse(throttle.onFlush("d1")) // the change already went through
    }

    @Test
    fun changeAfterTheIntervalGoesThroughAndClearsTheHeldMark() {
        var now = 0L
        val throttle = ReadingThrottle { now }
        throttle.onChange("d1")
        now = 10L
        throttle.onChange("d1") // held
        now = ReadingDebounce.INTERVAL_MS + 1
        assertTrue(throttle.onChange("d1"))
        assertFalse(throttle.onFlush("d1")) // already sent, nothing held
    }
}
