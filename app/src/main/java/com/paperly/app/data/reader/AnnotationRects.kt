package com.paperly.app.data.reader

import com.paperly.app.domain.reader.MatchRect
import org.json.JSONArray

private const val RECT_VALUES = 4

/** Multi-part rects (page fractions 0..1) <-> `annotations.rects` JSON. Tolerant: bad parts are dropped. */
object AnnotationRects {
    const val MAX_RECTS = 50

    /** null for 0–1 parts (the four rect columns already describe it). */
    fun encode(rects: List<MatchRect>): String? =
        if (rects.size < 2) {
            null
        } else {
            JSONArray(rects.take(MAX_RECTS).map { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) }).toString()
        }

    /** Valid parts only (finite, 0..1, ≤[MAX_RECTS]); empty if null/corrupt. */
    fun decode(json: String?): List<MatchRect> {
        val arr = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        val out = ArrayList<MatchRect>()
        for (i in 0 until minOf(arr.length(), MAX_RECTS)) {
            arr.optJSONArray(i)?.toRect()?.let(out::add)
        }
        return out
    }

    /** Re-encodes only the valid parts (backup restore); null if fewer than 2 survive. */
    fun normalize(json: String?): String? = encode(decode(json))

    fun bounding(rects: List<MatchRect>): MatchRect = MatchRect(
        rects.minOf { it.left },
        rects.minOf { it.top },
        rects.maxOf { it.right },
        rects.maxOf { it.bottom },
    )

    private fun JSONArray.toRect(): MatchRect? {
        if (length() != RECT_VALUES) return null
        val v = List(RECT_VALUES) { optDouble(it, Double.NaN) }
        if (!v.all { it.isFinite() && it in 0.0..1.0 }) return null
        val (l, t, r, b) = v
        return MatchRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
    }
}
