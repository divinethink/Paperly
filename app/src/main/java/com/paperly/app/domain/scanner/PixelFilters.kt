package com.paperly.app.domain.scanner

/**
 * Pure pixel maths on ARGB int arrays (no Android types, unit-testable). All functions return a NEW array and keep
 * alpha opaque.
 */
object PixelFilters {
    private const val CHANNEL = 255
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val SHIFT_A = 24
    private const val OPAQUE = 0xFF shl SHIFT_A
    private const val HISTOGRAM = 256
    private const val LOW_PERCENT = 0.01
    private const val HIGH_PERCENT = 0.99
    private const val MIN_RANGE = 16
    private const val BW_SENSITIVITY = 0.85f
    private const val MIN_RADIUS = 7
    private const val RADIUS_DIVISOR = 16
    private const val WEIGHT_R = 299
    private const val WEIGHT_G = 587
    private const val WEIGHT_B = 114
    private const val WEIGHT_SUM = 1000

    fun luma(argb: Int): Int {
        val r = (argb shr SHIFT_R) and CHANNEL
        val g = (argb shr SHIFT_G) and CHANNEL
        val b = argb and CHANNEL
        return (r * WEIGHT_R + g * WEIGHT_G + b * WEIGHT_B) / WEIGHT_SUM
    }

    private fun gray(v: Int) = OPAQUE or (v shl SHIFT_R) or (v shl SHIFT_G) or v

    fun gray(px: IntArray): IntArray = IntArray(px.size) { gray(luma(px[it])) }

    /** "Colour" = auto-levels: stretches 1%..99% of the brightness range to 0..255 (whitens paper, keeps hues). */
    fun enhanceColor(px: IntArray): IntArray {
        if (px.isEmpty()) return px.copyOf()
        val hist = IntArray(HISTOGRAM)
        px.forEach { hist[luma(it)]++ }
        val low = percentile(hist, px.size, LOW_PERCENT)
        val high = percentile(hist, px.size, HIGH_PERCENT)
        if (high - low < MIN_RANGE) return px.copyOf() // flat image: nothing to stretch
        fun stretch(c: Int) = ((c - low) * CHANNEL / (high - low)).coerceIn(0, CHANNEL)
        return IntArray(px.size) {
            val p = px[it]
            OPAQUE or (stretch((p shr SHIFT_R) and CHANNEL) shl SHIFT_R) or
                (stretch((p shr SHIFT_G) and CHANNEL) shl SHIFT_G) or stretch(p and CHANNEL)
        }
    }

    internal fun percentile(hist: IntArray, total: Int, fraction: Double): Int {
        val target = maxOf(1L, (total * fraction).toLong()) // >= 1: an empty bucket 0 must not win
        var seen = 0L
        for (i in hist.indices) {
            seen += hist[i]
            if (seen >= target) return i
        }
        return CHANNEL
    }

    /**
     * Black & white by local mean (Bradley): a pixel is black when darker than [BW_SENSITIVITY] x the mean of its
     * neighbourhood, so shadows and uneven light do not turn the page black. Integral image in Int arithmetic that
     * may overflow: window sums are differences, which stay correct modulo 2^32 (a window sum is far below 2^31).
     */
    fun blackWhite(px: IntArray, w: Int, h: Int): IntArray {
        require(w > 0 && h > 0 && px.size == w * h) { "size mismatch" }
        val lum = IntArray(px.size) { luma(px[it]) }
        val stride = w + 1
        val integral = IntArray(stride * (h + 1))
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) {
                row += lum[y * w + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        val radius = maxOf(MIN_RADIUS, minOf(w, h) / RADIUS_DIVISOR)
        return IntArray(px.size) { i ->
            val x = i % w
            val y = i / w
            val x0 = maxOf(0, x - radius)
            val x1 = minOf(w, x + radius + 1)
            val y0 = maxOf(0, y - radius)
            val y1 = minOf(h, y + radius + 1)
            val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] -
                integral[y1 * stride + x0] + integral[y0 * stride + x0]
            val count = (x1 - x0) * (y1 - y0)
            if (lum[i].toLong() * count < sum.toLong() * BW_SENSITIVITY) gray(0) else gray(CHANNEL)
        }
    }
}
