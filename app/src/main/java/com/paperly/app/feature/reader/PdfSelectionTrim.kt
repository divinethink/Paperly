package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect

private const val TRIM_NEAR_X = 0.03f // same margins as [clampEnds]
private const val TRIM_NEAR_Y = 0.012f

/** [isForward] across pages: a lower page number is always earlier. */
internal fun isForwardAcross(from: PagePoint, to: PagePoint, lineHeight: Float): Boolean =
    if (from.page != to.page) from.page < to.page else isForward(from.point, to.point, lineHeight)

private fun MatchRect.touches(y: Float) = y in (top - TRIM_NEAR_Y)..(bottom + TRIM_NEAR_Y)

/**
 * First page of a multi-page selection: the first line starts at the start touch [at] — only when that touch is
 * on the line, otherwise the engine's own snap stays.
 */
internal fun trimStart(rects: List<MatchRect>, at: Offset): List<MatchRect> {
    val first = rects.firstOrNull()?.takeIf { it.touches(at.y) } ?: return rects
    val left = maxOf(first.left, at.x - TRIM_NEAR_X)
    return if (left < first.right) listOf(first.copy(left = left)) + rects.drop(1) else rects
}

/** Last page of a multi-page selection: the last line ends at the end touch (same on-the-line rule as [trimStart]). */
internal fun trimEnd(rects: List<MatchRect>, at: Offset): List<MatchRect> {
    val last = rects.lastOrNull()?.takeIf { it.touches(at.y) } ?: return rects
    val right = minOf(last.right, at.x + TRIM_NEAR_X)
    return if (right > last.left) rects.dropLast(1) + last.copy(right = right) else rects
}
