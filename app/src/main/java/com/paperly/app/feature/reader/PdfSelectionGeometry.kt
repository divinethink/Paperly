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
    val forward = pickForward(rects, from, to)
    val start = if (forward) from else to
    val end = if (forward) to else from
    val out = rects.toMutableList()
    val left = maxOf(first.left, start.x - NEAR_X)
    if (left < first.right) out[0] = first.copy(left = left)
    val last = out.last()
    val right = minOf(last.right, end.x + NEAR_X)
    if (right > last.left) out[out.lastIndex] = last.copy(right = right)
    return dropOtherColumns(out, minOf(start.x, end.x) - NEAR_X, maxOf(start.x, end.x) + NEAR_X)
}

/**
 * Direction of a picked selection from [from] to [to]. A selection on one line is decided by left/right only: a
 * finger that drifts up while it drags along the line (the handle sits below the text) must not turn it backwards.
 * Several lines: reading order ([isForward]).
 */
internal fun pickForward(rects: List<MatchRect>, from: Offset, to: Offset): Boolean {
    val first = rects.firstOrNull() ?: return true
    return if (first.onLineOf(rects.last())) from.x <= to.x else isForward(from, to, first.bottom - first.top)
}

/**
 * Stream-order selection runs through every table cell between the two ends. A middle line outside the horizontal
 * band the finger actually covered ([bandLeft]..[bandRight]) is dropped; where the finger goes, the selection goes.
 */
private fun dropOtherColumns(rects: List<MatchRect>, bandLeft: Float, bandRight: Float): List<MatchRect> {
    if (rects.size <= 2) return rects
    return rects.filterIndexed { i, r -> i == 0 || i == rects.lastIndex || (r.right > bandLeft && r.left < bandRight) }
}

/**
 * The engine snaps a press to the nearest character, so on a page whose only text is a watermark any press would
 * select it. A pick counts only when the press is on or right beside its lines.
 */
internal fun isNear(rects: List<MatchRect>, p: Offset): Boolean = rects.any {
    p.x in (it.left - NEAR_X)..(it.right + NEAR_X) && p.y in (it.top - NEAR_Y)..(it.bottom + NEAR_Y)
}

/** The fixed end of a handle drag: its line rect, kept as it was grabbed. Null = that end is the one being moved. */
data class SelectionPin(val first: MatchRect? = null, val last: MatchRect? = null)

/** True when [to] lies after [from] in reading order (same line: further right; else further down). */
internal fun isForward(from: Offset, to: Offset, lineHeight: Float): Boolean =
    if (abs(from.y - to.y) < lineHeight * HALF) from.x <= to.x else from.y < to.y

private fun MatchRect.midY() = (top + bottom) * HALF

private fun MatchRect.onLineOf(other: MatchRect) = abs(midY() - other.midY()) < (other.bottom - other.top) * HALF

/**
 * The engine re-snaps the fixed end on every move and can slip a character (Bengali vowel signs sit left of their
 * consonant), so the fixed end would creep while the other handle is dragged. Put the grabbed line end back.
 */
internal fun pinEnds(rects: List<MatchRect>, pin: SelectionPin?): List<MatchRect> {
    if (pin == null || rects.isEmpty()) return rects
    val out = rects.toMutableList()
    pin.first?.let { p ->
        val head = out.first()
        if (head.onLineOf(p) && p.left < head.right) out[0] = head.copy(left = p.left)
    }
    pin.last?.let { p ->
        val tail = out.last()
        if (tail.onLineOf(p) && p.right > tail.left) out[out.lastIndex] = tail.copy(right = p.right)
    }
    return out
}
