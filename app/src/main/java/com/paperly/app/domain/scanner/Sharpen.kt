package com.paperly.app.domain.scanner

import kotlin.math.abs

/**
 * Light unsharp mask for text pages (Paperly / Docs / Clean): the brightness of every pixel is pushed away from the
 * 3 x 3 average around it, so letter edges get crisper. Flat areas (white paper, solid ink) have no difference and
 * stay exactly as they are; tiny differences (noise) are left alone. Works in place; extra memory is two byte arrays.
 */
object Sharpen {
    private const val CHANNEL = 255
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val TAPS = 3
    private const val NOISE = 6
    private const val AMOUNT = 0.7f

    fun apply(px: IntArray, w: Int, h: Int) {
        require(w > 0 && h > 0 && px.size == w * h) { "size mismatch" }
        val luma = ByteArray(px.size) { PixelFilters.luma(px[it]).toByte() }
        val blur = boxBlur(luma, w, h)
        for (i in px.indices) {
            val diff = (luma[i].toInt() and CHANNEL) - (blur[i].toInt() and CHANNEL)
            if (abs(diff) >= NOISE) px[i] = shifted(px[i], (diff * AMOUNT).toInt())
        }
    }

    private fun shifted(argb: Int, delta: Int): Int {
        val r = (((argb shr SHIFT_R) and CHANNEL) + delta).coerceIn(0, CHANNEL)
        val g = (((argb shr SHIFT_G) and CHANNEL) + delta).coerceIn(0, CHANNEL)
        val b = ((argb and CHANNEL) + delta).coerceIn(0, CHANNEL)
        return (argb and (CHANNEL shl (SHIFT_R + SHIFT_G))) or (r shl SHIFT_R) or (g shl SHIFT_G) or b
    }

    /** 3 x 3 average (edges repeat their border pixels), as two separable passes. */
    private fun boxBlur(src: ByteArray, w: Int, h: Int): ByteArray {
        val across = ByteArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val sum = at(src, row + maxOf(x - 1, 0)) + at(src, row + x) + at(src, row + minOf(x + 1, w - 1))
                across[row + x] = (sum / TAPS).toByte()
            }
        }
        val out = ByteArray(src.size)
        for (y in 0 until h) {
            val above = maxOf(y - 1, 0) * w
            val here = y * w
            val below = minOf(y + 1, h - 1) * w
            for (x in 0 until w) {
                out[here + x] = ((at(across, above + x) + at(across, here + x) + at(across, below + x)) / TAPS).toByte()
            }
        }
        return out
    }

    private fun at(bytes: ByteArray, i: Int) = bytes[i].toInt() and CHANNEL
}
