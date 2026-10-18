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
    private const val GRID_DIVISOR = 24
    private const val MIN_BLOCK = 8
    private const val MIN_BACKGROUND = 40
    private const val WHITE_POINT = 240
    private const val AUTO_CONTRAST = 1.1f
    private const val PHOTO_CONTRAST = 1.06f
    private const val PHOTO_SATURATION = 1.25f
    private const val MID = 128f
    private const val HALF = 0.5f

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

    /**
     * "Docs": removes shadows and uneven light but keeps colour. The paper brightness of each small block (its
     * per-channel maximum) is smoothed and interpolated into a background map; every pixel is divided by it so the
     * paper becomes white (a fixed white point, so sparse ink never greys the paper).
     */
    fun docs(px: IntArray, w: Int, h: Int): IntArray {
        require(w > 0 && h > 0 && px.size == w * h) { "size mismatch" }
        val block = maxOf(MIN_BLOCK, minOf(w, h) / GRID_DIVISOR)
        val gw = (w + block - 1) / block
        val gh = (h + block - 1) / block
        val bg = Array(CHANNELS) { IntArray(gw * gh) }
        for (y in 0 until h) {
            val row = (y / block) * gw
            for (x in 0 until w) {
                val p = px[y * w + x]
                val cell = row + x / block
                for (c in 0 until CHANNELS) bg[c][cell] = maxOf(bg[c][cell], channel(p, c))
            }
        }
        val smooth = Array(CHANNELS) { blur(bg[it], gw, gh) }
        val x0 = IntArray(w)
        val xt = FloatArray(w)
        for (x in 0 until w) {
            val f = ((x + HALF) / block - HALF).coerceIn(0f, (gw - 1).toFloat())
            x0[x] = f.toInt()
            xt[x] = f - x0[x]
        }
        val out = IntArray(px.size)
        for (y in 0 until h) {
            val fy = ((y + HALF) / block - HALF).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, gh - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val xa = x0[x]
                val xb = minOf(xa + 1, gw - 1)
                val tx = xt[x]
                val p = px[y * w + x]
                var argb = OPAQUE
                for (c in 0 until CHANNELS) {
                    val g = smooth[c]
                    val top = g[y0 * gw + xa] * (1 - tx) + g[y0 * gw + xb] * tx
                    val bottom = g[y1 * gw + xa] * (1 - tx) + g[y1 * gw + xb] * tx
                    val background = maxOf(MIN_BACKGROUND.toFloat(), top * (1 - ty) + bottom * ty)
                    val v = (channel(p, c) * CHANNEL / background * CHANNEL / WHITE_POINT).toInt().coerceIn(0, CHANNEL)
                    argb = argb or (v shl SHIFT_FOR[c])
                }
                out[y * w + x] = argb
            }
        }
        return out
    }

    /** "Auto": each colour channel is levelled on its own (removes colour casts), then a small contrast boost. */
    fun autoEnhance(px: IntArray): IntArray {
        if (px.isEmpty()) return px.copyOf()
        val low = IntArray(CHANNELS)
        val high = IntArray(CHANNELS)
        for (c in 0 until CHANNELS) {
            val hist = IntArray(HISTOGRAM)
            px.forEach { hist[channel(it, c)]++ }
            low[c] = percentile(hist, px.size, LOW_PERCENT)
            high[c] = percentile(hist, px.size, HIGH_PERCENT)
        }
        return IntArray(px.size) {
            var argb = OPAQUE
            for (c in 0 until CHANNELS) {
                val range = high[c] - low[c]
                val v = if (range < MIN_RANGE) channel(px[it], c) else (channel(px[it], c) - low[c]) * CHANNEL / range
                argb = argb or (contrast(v.coerceIn(0, CHANNEL), AUTO_CONTRAST) shl SHIFT_FOR[c])
            }
            argb
        }
    }

    /** "Photo": more colour and a little contrast, for pictures rather than paper. */
    fun photo(px: IntArray): IntArray = IntArray(px.size) {
        val p = px[it]
        val grey = luma(p)
        var argb = OPAQUE
        for (c in 0 until CHANNELS) {
            val v = (grey + (channel(p, c) - grey) * PHOTO_SATURATION).toInt().coerceIn(0, CHANNEL)
            argb = argb or (contrast(v, PHOTO_CONTRAST) shl SHIFT_FOR[c])
        }
        argb
    }

    private const val CHANNELS = 3
    private val SHIFT_FOR = intArrayOf(SHIFT_R, SHIFT_G, 0)

    private fun channel(argb: Int, index: Int) = (argb shr SHIFT_FOR[index]) and CHANNEL

    private fun contrast(v: Int, factor: Float) = ((v - MID) * factor + MID).toInt().coerceIn(0, CHANNEL)

    /** 3x3 box average on a [gw] x [gh] grid (edges use the cells that exist). */
    private fun blur(grid: IntArray, gw: Int, gh: Int): IntArray = IntArray(grid.size) { i ->
        val cx = i % gw
        val cy = i / gw
        var sum = 0
        var count = 0
        for (y in maxOf(0, cy - 1)..minOf(gh - 1, cy + 1)) {
            for (x in maxOf(0, cx - 1)..minOf(gw - 1, cx + 1)) {
                sum += grid[y * gw + x]
                count++
            }
        }
        sum / count
    }

    private fun percentile(hist: IntArray, total: Int, fraction: Double): Int {
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
