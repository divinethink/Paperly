package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect
import kotlin.math.abs

private const val NEAR_X = 0.03f // page fractions: a press this close to a picked line still counts as on it
private const val NEAR_Y = 0.012f
private const val HALF = 0.5f

/**
 * A drag that crosses table cells can come back as whole-line rects, or with other cells in between. The first line
 * starts at the start touch and the last line ends at the end touch, give or take [NEAR_X]; the engine's own
 * snapping stays inside that margin.
 */
internal fun clampEnds(rects: List<MatchRect>, from: Offset, to: Offset): List<MatchRect> {
    val first = rects.firstOrNull() ?: return rects
    val sameLine = abs(from.y - to.y) < (first.bottom - first.top) * HALF
    val forward = if (sameLine) from.x <= to.x else from.y < to.y
    val start = if (forward) from else to
    val end = if (forward) to else from
    val out = rects.toMutableList()
    val left = maxOf(first.left, start.x - NEAR_X)
    if (left < first.right) out[0] = first.copy(left = left)
    val last = out.last()
    val right = minOf(last.right, end.x + NEAR_X)
    if (right > last.left) out[out.lastIndex] = last.copy(right = right)
    return dropOtherColumns(out)
}

/**
 * Stream-order selection runs through every table cell between the two ends. A middle line that does not touch the
 * horizontal band of the first/last line (the other cells of the row, the serial-number column) is dropped.
 */
private fun dropOtherColumns(rects: List<MatchRect>): List<MatchRect> {
    if (rects.size <= 2) return rects
    val bandLeft = minOf(rects.first().left, rects.last().left)
    val bandRight = maxOf(rects.first().right, rects.last().right)
    return rects.filterIndexed { i, r -> i == 0 || i == rects.lastIndex || (r.right > bandLeft && r.left < bandRight) }
}

/**
 * The engine snaps a press to the nearest character, so on a page whose only text is a watermark any press would
 * select it. A pick counts only when the press is on or right beside its lines.
 */
internal fun isNear(rects: List<MatchRect>, p: Offset): Boolean = rects.any {
    p.x in (it.left - NEAR_X)..(it.right + NEAR_X) && p.y in (it.top - NEAR_Y)..(it.bottom + NEAR_Y)
}
