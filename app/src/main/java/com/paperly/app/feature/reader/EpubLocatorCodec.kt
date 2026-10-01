package com.paperly.app.feature.reader

import kotlin.math.floor
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

private const val BOOKMARK_BUCKETS = 20

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
