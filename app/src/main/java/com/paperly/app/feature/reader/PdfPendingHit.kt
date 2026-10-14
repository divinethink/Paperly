package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.paperly.app.domain.reader.MatchRect

private const val SIDE_MIN = 0.01f // narrowest box (page fraction) a side handle can leave

private fun MatchRect.toRect(size: IntSize) =
    Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

/** What a finger-down on the pending parts of a page grabbed. */
internal enum class HitKind { REMOVE, RESIZE, RESIZE_X, MOVE }

/** [fixed] = the opposite corner (px) that stays put while a RESIZE handle is dragged. */
internal data class PendingHit(val index: Int, val kind: HitKind, val fixed: Offset)

/** A grab point of a pending box; [fixed] is the opposite corner/side that stays put (RESIZE, RESIZE_X). */
private class Handle(val at: Offset, val kind: HitKind, val fixed: Offset)

private fun Rect.handles(): List<Handle> = listOf(
    Handle(topRight, HitKind.REMOVE, Offset.Zero),
    Handle(topLeft, HitKind.RESIZE, bottomRight),
    Handle(bottomLeft, HitKind.RESIZE, topRight),
    Handle(bottomRight, HitKind.RESIZE, topLeft),
    Handle(centerLeft, HitKind.RESIZE_X, centerRight),
    Handle(centerRight, HitKind.RESIZE_X, centerLeft),
)

/** New box when a side handle is dragged to [x]: only the width changes; null if it would get too narrow. */
internal fun dragSide(fixedX: Float, x: Float, base: MatchRect, size: IntSize): MatchRect? {
    if (size.width <= 0) return null
    val w = size.width.toFloat()
    val moved = MatchRect(
        left = (minOf(fixedX, x) / w).coerceIn(0f, 1f),
        top = base.top,
        right = (maxOf(fixedX, x) / w).coerceIn(0f, 1f),
        bottom = base.bottom,
    )
    return moved.takeIf { it.right - it.left >= SIDE_MIN }
}

/** Topmost pending part under [pos]: nearest handle within [reach] (badge, corners, side middles), else body. */
internal fun hitPending(pos: Offset, size: IntSize, rects: List<MatchRect>, reach: Float): PendingHit? {
    for (i in rects.indices.reversed()) {
        val r = rects[i].toRect(size)
        val handle = r.handles().filter { (pos - it.at).getDistance() <= reach }
            .minByOrNull { (pos - it.at).getDistance() }
        val hit = when {
            handle != null -> PendingHit(i, handle.kind, handle.fixed)
            r.contains(pos) -> PendingHit(i, HitKind.MOVE, Offset.Zero)
            else -> null
        }
        if (hit != null) return hit
    }
    return null
}
