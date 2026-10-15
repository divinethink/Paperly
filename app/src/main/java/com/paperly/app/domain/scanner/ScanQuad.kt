package com.paperly.app.domain.scanner

import kotlin.math.abs
import kotlin.math.hypot

data class ScanPoint(val x: Float, val y: Float)

/**
 * Crop corners as fractions (0..1) of the source image, order: top-left, top-right, bottom-right, bottom-left
 * (clockwise). [isValid] = inside the image, convex, not degenerate.
 */
data class ScanQuad(val tl: ScanPoint, val tr: ScanPoint, val br: ScanPoint, val bl: ScanPoint) {
    val points: List<ScanPoint> get() = listOf(tl, tr, br, bl)

    fun toJson(): String = points.joinToString(prefix = "[", postfix = "]", separator = ",") { "${it.x},${it.y}" }

    fun isValid(): Boolean = points.all { it.x in 0f..1f && it.y in 0f..1f } && isConvex() && area() >= MIN_AREA

    private fun isConvex(): Boolean {
        val p = points
        return p.indices.all { i ->
            val a = p[i]
            val b = p[(i + 1) % CORNERS]
            val c = p[(i + 2) % CORNERS]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0f
        }
    }

    /** Shoelace area (fraction of the image). */
    fun area(): Float {
        val p = points
        var sum = 0f
        for (i in p.indices) {
            val q = p[(i + 1) % CORNERS]
            sum += p[i].x * q.y - q.x * p[i].y
        }
        return abs(sum) / 2f
    }

    companion object {
        const val CORNERS = 4
        private const val VALUES = CORNERS * 2
        const val MIN_AREA = 0.02f
        val FULL = ScanQuad(ScanPoint(0f, 0f), ScanPoint(1f, 0f), ScanPoint(1f, 1f), ScanPoint(0f, 1f))

        /** From the four corners in order tl, tr, br, bl. */
        fun of(p: List<ScanPoint>) = ScanQuad(p.first(), p[1], p[2], p.last())

        /** Null for missing/garbled/invalid input (callers then use [FULL]); never throws. */
        fun fromJson(json: String?): ScanQuad? {
            val parts = json?.trim()?.removePrefix("[")?.removeSuffix("]")?.split(',')
            val numbers = parts?.map { it.trim().toFloatOrNull() }
            if (numbers == null || numbers.size != VALUES || numbers.any { it == null }) return null
            val v = numbers.filterNotNull()
            val p = List(CORNERS) { ScanPoint(v[it * 2], v[it * 2 + 1]) }
            return of(p).takeIf { it.isValid() }
        }

        /** Size in pixels of the flattened page: longest opposite edges, scaled down (never up) to [maxSide]. */
        fun outputSize(q: ScanQuad, srcW: Int, srcH: Int, maxSide: Int): Pair<Int, Int> {
            fun len(a: ScanPoint, b: ScanPoint) = hypot((a.x - b.x) * srcW, (a.y - b.y) * srcH)
            val w = maxOf(len(q.tl, q.tr), len(q.bl, q.br))
            val h = maxOf(len(q.tl, q.bl), len(q.tr, q.br))
            val scale = minOf(1f, maxSide / maxOf(w, h, 1f))
            return maxOf(1, (w * scale).toInt()) to maxOf(1, (h * scale).toInt())
        }
    }
}
