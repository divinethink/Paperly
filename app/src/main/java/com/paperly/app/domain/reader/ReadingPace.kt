package com.paperly.app.domain.reader

import kotlin.math.ceil

private const val MAX_STEP = 0.1f
private const val MAX_GAP_MS = 300_000L
private const val MIN_GAP_MS = 1_000L
private const val MIN_PROGRESS = 0.01f
private const val MIN_ACTIVE_MS = 30_000L
private const val MS_PER_MINUTE = 60_000f
private const val ROUND_EPSILON = 0.01f // float noise (8.0000001 min) must not round up to 9

/**
 * Session-local reading pace. Only small forward steps count (jumps and long idle gaps are ignored),
 * so "minutes left" is a rough estimate and stays null until there is enough data. Nothing is stored.
 */
class ReadingPace {
    private var lastFraction = -1f
    private var lastAt = 0L
    private var progress = 0f
    private var activeMs = 0L

    fun record(fraction: Float, nowMs: Long) {
        val step = fraction - lastFraction
        val gap = nowMs - lastAt
        val forward = step > 0f && step <= MAX_STEP
        if (lastFraction >= 0f && forward && gap in MIN_GAP_MS..MAX_GAP_MS) {
            progress += step
            activeMs += gap
        }
        lastFraction = fraction
        lastAt = nowMs
    }

    /** Rounded-up minutes to finish from [fraction], or null while the estimate is not reliable yet. */
    fun minutesLeft(fraction: Float): Int? {
        if (progress < MIN_PROGRESS || activeMs < MIN_ACTIVE_MS) return null
        val remaining = (1f - fraction).coerceAtLeast(0f)
        val minutes = remaining * activeMs / progress / MS_PER_MINUTE
        return ceil(minutes - ROUND_EPSILON).toInt().coerceAtLeast(0)
    }
}
