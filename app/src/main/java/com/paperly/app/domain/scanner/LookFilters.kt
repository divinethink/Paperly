package com.paperly.app.domain.scanner

import kotlin.math.pow

/**
 * "Look" filters (Docs / Auto / Photo): pure pixel maths on ARGB int arrays, no Android types. Every function returns
 * a NEW array and keeps alpha opaque. Kept apart from [PixelFilters] (levels, grey, black & white).
 */
object LookFilters {
    private const val CHANNEL = 255
    private const val CHANNELS = 3
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val SHIFT_A = 24
    private const val OPAQUE = 0xFF shl SHIFT_A
    private const val HISTOGRAM = 256
    private const val LOW_PERCENT = 0.01
    private const val HIGH_PERCENT = 0.99
    private const val MIN_RANGE = 16
    private const val GRID_DIVISOR = 24
    private const val MIN_BLOCK = 8
    private const val MIN_BACKGROUND = 40
    private const val WHITE_POINT = 240
    private const val AUTO_CONTRAST = 1.1f
    private const val CLEAN_KEEP = 185
    private const val CLEAN_WHITE = 222
    private const val CLEAN_GAMMA = 1.4f
    private const val INK_R = 70
    private const val INK_G = 45
    private const val INK_B = 25
    private const val PAPER_R = 250
    private const val PAPER_G = 236
    private const val PAPER_B = 210
    private const val PHOTO_CONTRAST = 1.06f
    private const val PHOTO_SATURATION = 1.25f
    private const val MID = 128f
    private const val HALF = 0.5f
    private const val PERCENT = 100f
    private const val BRIGHTNESS_STEP = 1.28f
    private val SHIFT_FOR = intArrayOf(SHIFT_R, SHIFT_G, 0)

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
                    val white = channel(p, c) * CHANNEL / background * CHANNEL / WHITE_POINT
                    argb = argb or (white.toInt().coerceIn(0, CHANNEL) shl SHIFT_FOR[c])
                }
                out[y * w + x] = argb
            }
        }
        return out
    }

    /**
     * "Clean": [docs] shadow removal, then grey through a smooth curve: greys up to [CLEAN_KEEP] (letters) are
     * darkened a little (gamma, no steps, so thin strokes never break up); the light range [CLEAN_KEEP]..[CLEAN_WHITE]
     * (faint shadow, fold marks) is lifted to pure white.
     */
    fun clean(px: IntArray, w: Int, h: Int): IntArray {
        val flat = docs(px, w, h)
        val curve = IntArray(HISTOGRAM) { grey ->
            val v = if (grey <= CLEAN_KEEP) {
                CLEAN_KEEP * (grey.toFloat() / CLEAN_KEEP).pow(CLEAN_GAMMA)
            } else {
                CLEAN_KEEP + (CHANNEL - CLEAN_KEEP) * ((grey - CLEAN_KEEP).toFloat() / (CLEAN_WHITE - CLEAN_KEEP))
                    .coerceAtMost(1f)
            }
            v.toInt().coerceIn(0, CHANNEL)
        }
        return IntArray(flat.size) {
            val v = curve[PixelFilters.luma(flat[it]).coerceIn(0, CHANNEL)]
            OPAQUE or (v shl SHIFT_R) or (v shl SHIFT_G) or v
        }
    }

    /** "Sepia": the [PaperlyFilter] page printed in warm tones: cream paper, dark brown ink. */
    fun sepia(px: IntArray, w: Int, h: Int): IntArray {
        val base = PaperlyFilter.apply(px, w, h)
        return IntArray(base.size) {
            val v = PixelFilters.luma(base[it])
            val r = mix(INK_R, PAPER_R, v)
            val g = mix(INK_G, PAPER_G, v)
            OPAQUE or (r shl SHIFT_R) or (g shl SHIFT_G) or mix(INK_B, PAPER_B, v)
        }
    }

    private fun mix(ink: Int, paper: Int, v: Int) = ink + (paper - ink) * v / CHANNEL

    /** "Auto": each colour channel is levelled on its own (removes colour casts), then a small contrast boost. */
    fun autoEnhance(px: IntArray): IntArray {
        if (px.isEmpty()) return px.copyOf()
        val low = IntArray(CHANNELS)
        val high = IntArray(CHANNELS)
        for (c in 0 until CHANNELS) {
            val hist = IntArray(HISTOGRAM)
            px.forEach { hist[channel(it, c)]++ }
            low[c] = PixelFilters.percentile(hist, px.size, LOW_PERCENT)
            high[c] = PixelFilters.percentile(hist, px.size, HIGH_PERCENT)
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
        val grey = PixelFilters.luma(p)
        var argb = OPAQUE
        for (c in 0 until CHANNELS) {
            val v = (grey + (channel(p, c) - grey) * PHOTO_SATURATION).toInt().coerceIn(0, CHANNEL)
            argb = argb or (contrast(v, PHOTO_CONTRAST) shl SHIFT_FOR[c])
        }
        argb
    }

    /**
     * Manual adjustment: [brightness] -100..100 shifts every channel (up to +-128), [contrast] -50..100 scales it
     * around mid-grey (0.5x..2x). 0/0 returns the pixels unchanged.
     */
    fun adjust(px: IntArray, brightness: Int, contrast: Int): IntArray {
        if (brightness == 0 && contrast == 0) return px.copyOf()
        val factor = 1f + contrast / PERCENT
        val shift = brightness * BRIGHTNESS_STEP
        val lut = IntArray(CHANNEL + 1) { ((it - MID) * factor + MID + shift).toInt().coerceIn(0, CHANNEL) }
        return IntArray(px.size) {
            val p = px[it]
            OPAQUE or (lut[channel(p, 0)] shl SHIFT_R) or (lut[channel(p, 1)] shl SHIFT_G) or lut[channel(p, 2)]
        }
    }

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
}
