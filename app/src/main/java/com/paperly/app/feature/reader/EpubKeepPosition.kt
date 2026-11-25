package com.paperly.app.feature.reader

import kotlin.math.abs
import kotlinx.coroutines.delay
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi

private const val SETTLE_MS = 400L // time the book needs to re-lay out after a typography change
private const val DRIFT = 0.02 // 2% of a chapter

/** True when the reading point moved noticeably inside the chapter (null = unknown, not counted as moved). */
internal fun progressionDrifted(before: Double?, after: Double?): Boolean =
    if (before != null && after != null) abs(before - after) > DRIFT else false

/**
 * Pushes new preferences, then checks that the reading point stayed put. A size/margin change re-flows the text,
 * and the page can end up elsewhere; if so the reader is taken back to where it was (no change = no jump).
 */
@OptIn(ExperimentalReadiumApi::class)
internal suspend fun EpubNavigatorFragment.submitKeepingPosition(prefs: EpubPreferences) {
    val before = currentLocator.value
    submitPreferences(prefs)
    delay(SETTLE_MS)
    val after = currentLocator.value
    val moved = before.href != after.href ||
        progressionDrifted(before.locations.progression, after.locations.progression)
    if (moved) go(before, animated = false)
}
