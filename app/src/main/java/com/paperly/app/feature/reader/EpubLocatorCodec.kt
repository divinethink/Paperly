package com.paperly.app.feature.reader

import kotlin.math.floor
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

private const val BOOKMARK_BUCKETS = 20
private const val END_PROGRESSION = 0.98
private const val END_TOTAL_PROGRESSION = 0.985
private const val END_POSITIONS_LEFT = 2.5

internal fun encodeLocator(locator: Locator): String = locator.toJSON().toString()

/** Null for missing/corrupt JSON, so a bad row just means "open at the start". */
internal fun decodeLocator(json: String?): Locator? =
    json?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

/** Bookmark identity and stored value: same chapter + same 5% slice of it = the same bookmark. */
internal fun bookmarkKey(locator: Locator): String {
    val bucket = floor((locator.locations.progression ?: 0.0) * BOOKMARK_BUCKETS).toInt()
    val snapped = locator.copy(
        locations = Locator.Locations(progression = bucket / BOOKMARK_BUCKETS.toDouble()),
        text = Locator.Text(),
    )
    return encodeLocator(snapped)
}

internal fun progressOf(locator: Locator): Float =
    (locator.locations.totalProgression ?: locator.locations.progression ?: 0.0).toFloat()

/**
 * Readium counts ~1 KB "positions" and reports where the visible page STARTS, so the last page of a book never
 * reads 100% (a 3-page last chapter ends at 67%). In the last reading-order file we report 100% once the page is
 * the last one, judged by any of: chapter progression, whole-book progression, or at most ~2 positions left
 * (position and total progression give the remaining count). Anywhere else the locator is unchanged.
 */
internal fun withBookEnd(locator: Locator, lastHref: Url?): Locator {
    val atEnd = lastHref != null && sameResource(locator.href, lastHref) && reachedEnd(locator.locations)
    return if (atEnd) locator.copy(locations = locator.locations.copy(totalProgression = 1.0)) else locator
}

private fun sameResource(a: Url, b: Url): Boolean = pathOf(a) == pathOf(b)

private fun pathOf(url: Url): String = url.toString().substringBefore('#').substringBefore('?')

private fun reachedEnd(locations: Locator.Locations): Boolean {
    val total = locations.totalProgression ?: 0.0
    val position = locations.position ?: 0
    val left = if (total > 0.0) position * (1.0 / total - 1.0) else Double.MAX_VALUE
    return (locations.progression ?: 0.0) >= END_PROGRESSION || total >= END_TOTAL_PROGRESSION ||
        left <= END_POSITIONS_LEFT
}
