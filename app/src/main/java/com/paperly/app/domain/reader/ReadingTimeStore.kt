package com.paperly.app.domain.reader

import kotlinx.coroutines.flow.Flow

/** Local-only reading time for the current local day. Nothing here ever leaves the device. */
interface ReadingTimeStore {
    /** Whole minutes read today (0 after midnight until something is read). */
    val todayMinutes: Flow<Int>

    /** Adds to today's total; a new day starts from zero. */
    suspend fun addSeconds(seconds: Long)
}

private const val MS_PER_SECOND = 1000L
private const val MAX_SPAN_SECONDS = 30L * 60

/**
 * Seconds to count for one foreground span. Capped so a reader left open (screen on, nobody reading)
 * cannot inflate the total; negative/clock-skew spans count as zero.
 */
fun countedSeconds(elapsedMs: Long): Long = (elapsedMs / MS_PER_SECOND).coerceIn(0L, MAX_SPAN_SECONDS)
