package com.paperly.app.domain.scanner

import kotlin.math.pow

/**
 * The "Paperly" filter: shadows, folds and uneven light disappear, the paper becomes pure white, letters turn dark and
 * a little bolder, and coloured ink (a red pen, say) keeps its colour. Pure pixel maths on ARGB int arrays.
 *
 * 1. Paper brightness per colour: the brightest value of every small cell (letters are thinner than a cell, so they
 *    drop out), then the median of a 5 x 5 block of cells (thin dark lines, like a fold, drop out too), then bilinear
 *    smoothing between cells.
 * 2. Every pixel is divided by that paper brightness (white balance and shadow removal in one step).
 * 3. The brightness of each pixel goes through a curve (pure white above [WHITE], darker letters below [KEEP]); the
 *    pixel's colours are scaled by the same factor, so hues stay. Letters are first mixed with their darkest
 *    2 x 2 neighbour, which makes thin strokes slightly bolder.
 */
object PaperlyFilter {
    private const val CHANNEL = 255
    private const val CHANNELS = 3
    private const val SHIFT_R = 16
    private const val SHIFT_G = 8
    private const val SHIFT_A = 24
    private const val OPAQUE = 0xFF shl SHIFT_A
    private const val HISTOGRAM = 256
    private const val GRID_DIVISOR = 200
    private const val MIN_CELL = 2
    private const val MEDIAN_RADIUS = 2
    private const val MIN_BACKGROUND = 40
    private const val BOLD_MIX = 0.5f
    private const val KEEP = 200
    private const val WHITE = 228
    private const val GAMMA = 2f
    private const val HALF = 0.5f
    private val SHIFT_FOR = intArrayOf(SHIFT_R, SHIFT_G, 0)

    fun apply(px: IntArray, w: Int, h: Int): IntArray {
        require(w > 0 && h > 0 && px.size == w * h) { "size mismatch" }
        val cell = maxOf(MIN_CELL, minOf(w, h) / GRID_DIVISOR)
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val paper = cellMax(px, w, h, cell).map { median(it, gw, gh) }
        val out = divideByPaper(px, w, h, cell, paper)
        applyCurve(out, w, h)
        return out
    }

    /** Brightest value of every [cell] x [cell] square, per colour channel. */
    private fun cellMax(px: IntArray, w: Int, h: Int, cell: Int): List<IntArray> {
        val gw = (w + cell - 1) / cell
        val grids = List(CHANNELS) { IntArray(gw * ((h + cell - 1) / cell)) }
        for (y in 0 until h) {
            val row = (y / cell) * gw
            for (x in 0 until w) {
                val p = px[y * w + x]
                val at = row + x / cell
                for (c in 0 until CHANNELS) grids[c][at] = maxOf(grids[c][at], (p shr SHIFT_FOR[c]) and CHANNEL)
            }
        }
        return grids
    }

    /** Median of the (2 * [MEDIAN_RADIUS] + 1) squared block around every cell (edges use the cells that exist). */
    private fun median(grid: IntArray, gw: Int, gh: Int): IntArray {
        val window = IntArray((2 * MEDIAN_RADIUS + 1) * (2 * MEDIAN_RADIUS + 1))
        return IntArray(grid.size) { i ->
            val cx = i % gw
            val cy = i / gw
            var n = 0
            for (y in maxOf(0, cy - MEDIAN_RADIUS)..minOf(gh - 1, cy + MEDIAN_RADIUS)) {
                for (x in maxOf(0, cx - MEDIAN_RADIUS)..minOf(gw - 1, cx + MEDIAN_RADIUS)) {
                    window[n++] = grid[y * gw + x]
                }
            }
            window.sort(0, n)
            window[n / 2]
        }
    }

    /** Every colour of [px] divided by the smoothed paper brightness (bilinear between cell centres). */
    private fun divideByPaper(px: IntArray, w: Int, h: Int, cell: Int, paper: List<IntArray>): IntArray {
        val out = IntArray(px.size)
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val x0 = IntArray(w)
        val xt = FloatArray(w)
        for (x in 0 until w) {
            val f = ((x + HALF) / cell - HALF).coerceIn(0f, (gw - 1).toFloat())
            x0[x] = f.toInt()
            xt[x] = f - x0[x]
        }
        for (y in 0 until h) {
            val fy = ((y + HALF) / cell - HALF).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, gh - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val xa = x0[x]
                val xb = minOf(xa + 1, gw - 1)
                val p = px[y * w + x]
                var argb = OPAQUE
                for (c in 0 until CHANNELS) {
                    val g = paper[c]
                    val top = g[y0 * gw + xa] * (1 - xt[x]) + g[y0 * gw + xb] * xt[x]
                    val bottom = g[y1 * gw + xa] * (1 - xt[x]) + g[y1 * gw + xb] * xt[x]
                    val bg = maxOf(MIN_BACKGROUND.toFloat(), top * (1 - ty) + bottom * ty)
                    val v = (((p shr SHIFT_FOR[c]) and CHANNEL) * CHANNEL / bg).toInt().coerceIn(0, CHANNEL)
                    argb = argb or (v shl SHIFT_FOR[c])
                }
                out[y * w + x] = argb
            }
        }
        return out
    }

    /** In place, top-left to bottom-right: only right / lower neighbours are read, and those are not changed yet. */
    private fun applyCurve(px: IntArray, w: Int, h: Int) {
        val curve = IntArray(HISTOGRAM) { grey ->
            val v = if (grey <= KEEP) {
                KEEP * (grey.toFloat() / KEEP).pow(GAMMA)
            } else {
                KEEP + (CHANNEL - KEEP) * ((grey - KEEP).toFloat() / (WHITE - KEEP)).coerceAtMost(1f)
            }
            v.toInt().coerceIn(0, CHANNEL)
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val right = if (x < w - 1) i + 1 else i
                val below = if (y < h - 1) i + w else i
                val grey = PixelFilters.luma(px[i])
                val darkest = minOf(
                    grey,
                    PixelFilters.luma(px[right]),
                    PixelFilters.luma(px[below]),
                    PixelFilters.luma(px[if (x < w - 1) below + 1 else below]),
                )
                val bolder = (grey + (darkest - grey) * BOLD_MIX).toInt().coerceIn(0, CHANNEL)
                val scale = curve[bolder].toFloat() / maxOf(grey, 1)
                var argb = OPAQUE
                for (c in 0 until CHANNELS) {
                    val v = (((px[i] shr SHIFT_FOR[c]) and CHANNEL) * scale).toInt().coerceIn(0, CHANNEL)
                    argb = argb or (v shl SHIFT_FOR[c])
                }
                px[i] = argb
            }
        }
    }
}
