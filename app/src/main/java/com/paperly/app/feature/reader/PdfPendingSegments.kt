package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.domain.reader.MatchRect

internal val HandleReach = 24.dp // touch reach around a handle: 48dp target
internal val PendingStrokeColor = Color(0xFF00796B)
private val PendingFill = Color(0x3300796B)
private val BadgeFill = Color(0xFFD32F2F)
private val HandleRadius = 7.dp
private val BadgeRadius = 11.dp
private val PendingLine = 1.dp
private const val SIDE_MIN = 0.01f // narrowest box (page fraction) a side handle can leave
private const val BADGE_CROSS = 0.5f // half-length of the white X, as a fraction of the badge radius

/** What a finger-down on the pending parts of a page grabbed. */
internal enum class HitKind { REMOVE, RESIZE, RESIZE_X, MOVE }

/** [fixed] = the opposite corner (px) that stays put while a RESIZE handle is dragged. */
internal data class PendingHit(val index: Int, val kind: HitKind, val fixed: Offset)

/** Pending-part wiring handed down to each page. */
data class PendingHooks(
    val state: PendingParts?,
    val onChange: (Int, MatchRect) -> Unit,
    val onRemove: (Int) -> Unit,
    val quick: QuickHooks? = null,
)

/** Everything the annotate-mode gesture of one page needs; [rects] = this page's pending parts. */
internal class PendingEdit(
    val rects: List<MatchRect>,
    val onDraft: (MatchRect?) -> Unit,
    val onFinger: (Offset?) -> Unit,
    val onCreate: (MatchRect) -> Unit,
    val pending: PendingHooks,
    val onQuick: ((MatchRect) -> Unit)?,
)

internal fun pendingEdit(
    index: Int,
    hooks: AnnotationHooks,
    onDraft: (MatchRect?) -> Unit,
    onFinger: (Offset?) -> Unit,
): PendingEdit = PendingEdit(
    rects = hooks.pending.state?.takeIf { it.page == index }?.rects.orEmpty(),
    onDraft = onDraft,
    onFinger = onFinger,
    onCreate = { hooks.onCreate(index, it) },
    pending = hooks.pending,
    onQuick = hooks.pending.quick?.let { quick -> { rect: MatchRect -> quick.onStart(index, rect) } },
)

private fun MatchRect.toRect(size: IntSize) =
    Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

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

/**
 * One annotate-mode gesture: grab a pending part (remove / resize / move) or, on empty space, draw a new part.
 * [edit] is read at touch-down so it reflects the latest parts.
 */
internal suspend fun AwaitPointerEventScope.pendingGesture(
    reachBase: Float,
    zoomOf: () -> Float,
    drawNew: Boolean = true,
    edit: () -> PendingEdit,
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val current = edit()
    val hit = hitPending(down.position, size, current.rects, reachBase / maxOf(zoomOf(), MIN_ZOOM))
    when {
        hit == null -> if (drawNew) dragNew(down, current)
        hit.kind == HitKind.REMOVE -> removeOnTap(down, hit.index, current)
        else -> dragExisting(down, hit, current)
    }
}

private suspend fun AwaitPointerEventScope.removeOnTap(down: PointerInputChange, index: Int, edit: PendingEdit) {
    down.consume()
    val up = waitForUpOrCancellation() ?: return
    up.consume()
    edit.pending.onRemove(index)
}

private fun MatchRect.shifted(dx: Float, dy: Float): MatchRect {
    val w = right - left
    val h = bottom - top
    val l = (left + dx).coerceIn(0f, 1f - w)
    val t = (top + dy).coerceIn(0f, 1f - h)
    return MatchRect(l, t, l + w, t + h)
}

private suspend fun AwaitPointerEventScope.dragExisting(down: PointerInputChange, hit: PendingHit, edit: PendingEdit) {
    down.consume()
    val start = edit.rects[hit.index]
    drag(down.id) { change ->
        change.consume()
        edit.onFinger(change.position)
        val next = when (hit.kind) {
            HitKind.RESIZE -> dragRect(hit.fixed, change.position, size)
            HitKind.RESIZE_X -> dragSide(hit.fixed.x, change.position.x, start, size)
            else -> start.shifted(
                (change.position.x - down.position.x) / size.width,
                (change.position.y - down.position.y) / size.height,
            )
        }
        next?.let { edit.pending.onChange(hit.index, it) }
    }
    edit.onFinger(null)
}

/** Touch-slop, then drag from the touch-down point; a cancelled drag (e.g. second finger) adds nothing. */
private suspend fun AwaitPointerEventScope.dragNew(down: PointerInputChange, edit: PendingEdit) {
    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return
    var end = slop.position
    edit.onDraft(dragRect(down.position, end, size))
    edit.onFinger(end)
    val finished = drag(slop.id) { change ->
        change.consume()
        end = change.position
        edit.onDraft(dragRect(down.position, end, size))
        edit.onFinger(end)
    }
    edit.onDraft(null)
    edit.onFinger(null)
    if (finished) dragRect(down.position, end, size)?.let(edit.onCreate)
}

/** Pending parts: resize handles (3 corners, left/right middles) + red remove badge; constant size on screen. */
internal fun DrawScope.drawPending(rects: List<MatchRect>, zoom: Float) {
    val z = maxOf(zoom, MIN_ZOOM)
    val line = PendingLine.toPx() / z
    val badge = BadgeRadius.toPx() / z
    rects.forEach { m ->
        val r = Rect(m.left * size.width, m.top * size.height, m.right * size.width, m.bottom * size.height)
        drawRect(PendingFill, r.topLeft, r.size)
        drawRect(PendingStrokeColor, r.topLeft, r.size, style = Stroke(line))
        val dot = HandleRadius.toPx() / z
        listOf(r.topLeft, r.bottomLeft, r.bottomRight, r.centerLeft, r.centerRight)
            .forEach { drawCircle(PendingStrokeColor, dot, it) }
        drawCircle(BadgeFill, badge, r.topRight)
        val c = r.topRight
        val h = badge * BADGE_CROSS
        drawLine(Color.White, Offset(c.x - h, c.y - h), Offset(c.x + h, c.y + h), line * 2)
        drawLine(Color.White, Offset(c.x - h, c.y + h), Offset(c.x + h, c.y - h), line * 2)
    }
}
