package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.ReaderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SelectionFill = Color(0x5542A5F5)
private val SelectHandleColor = Color(0xFF1E88E5)
private val SelectHandleRadius = 8.dp
private const val EDGE_INSET = 0.002f // anchors sit just inside the line ends, so the end characters stay selected
private const val HALF = 0.5f

/** Text picked on one [page]: line [rects] (page fractions) and its plain [text]. Not persisted. */
data class PdfSelection(val page: Int, val rects: List<MatchRect>, val text: String)

/** Long-press + drag text selection for text-layer PDF pages (P3-K). Latest drag position wins. */
class PdfTextSelectController(
    private val scope: CoroutineScope,
    private val engine: () -> ReaderEngine?,
) {
    private val selectionState = MutableStateFlow<PdfSelection?>(null)
    val state: StateFlow<PdfSelection?> = selectionState.asStateFlow()
    private var job: Job? = null

    /** Called when a press/drag lands where there is no text (scan, watermark-only layer): the quick box takes over. */
    var onMiss: ((Int, Offset, Offset?) -> Unit)? = null

    /** Selects between two page-fraction points; a drag over blank space keeps the previous selection. */
    fun update(page: Int, from: Offset, to: Offset) {
        job?.cancel()
        job = scope.launch {
            val pdf = engine() ?: return@launch
            val picked = pdf.selectText(page, from.x, from.y, to.x, to.y)?.takeIf { isNear(it.rects, from) }
            if (picked != null) {
                selectionState.value = PdfSelection(page, clampEnds(picked.rects, from, to), picked.text)
            } else if (selectionState.value == null) {
                onMiss?.invoke(page, from, to)
            }
        }
    }

    /** Long-press without drag: selects the word under [at] (page fractions). */
    fun selectWord(page: Int, at: Offset) {
        job?.cancel()
        job = scope.launch {
            val pdf = engine() ?: return@launch
            val picked = pickWordAt(pdf, page, at)?.takeIf { isNear(it.rects, at) }
            if (picked != null) {
                selectionState.value = PdfSelection(page, picked.rects, picked.text.trim())
            } else {
                onMiss?.invoke(page, at, null)
            }
        }
    }

    fun clear() {
        job?.cancel()
        selectionState.value = null
    }
}

/** Selection wiring handed down to each page; null = text selection off (annotate mode, no readable text layer). */
data class SelectHooks(
    val selection: PdfSelection?,
    val onUpdate: (Int, Offset, Offset) -> Unit,
    val onWord: (Int, Offset) -> Unit,
    val onClear: () -> Unit,
)

/** One page's view of [SelectHooks]; [onFinger] drives the loupe. */
internal class SelectEdit(
    val page: Int,
    val hooks: SelectHooks,
    val lock: GestureLock,
    val onFinger: (Offset?) -> Unit,
)

/** Offered for any readable text layer, Unicode Bengali included (rects are geometry; only Copy is lossy). */
@Composable
internal fun rememberSelectHooks(viewModel: ReaderViewModel, enabled: Boolean): SelectHooks? {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val selection by viewModel.textSelect.state.collectAsStateWithLifecycle()
    val controller = viewModel.textSelect
    return if (!enabled || !search.available) {
        null
    } else {
        remember(selection, controller) {
            SelectHooks(selection, controller::update, controller::selectWord, controller::clear)
        }
    }
}

private fun fraction(p: Offset, size: IntSize) =
    Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))

/**
 * Long-press selects the word under the finger; dragging on from there selects the text up to the finger.
 * A move before the long-press fires returns without consuming, so page scroll and pinch-zoom are untouched.
 */
internal suspend fun AwaitPointerEventScope.textSelectGesture(edit: SelectEdit) {
    val down = awaitFirstDown(requireUnconsumed = true) // a grabbed handle already consumed its down
    val pressed = awaitLongPressOrCancellation(down.id) ?: return
    if (size.width <= 0 || size.height <= 0) return
    pressed.consume()
    val start = fraction(down.position, size)
    edit.hooks.onClear()
    edit.hooks.onWord(edit.page, start)
    edit.onFinger(pressed.position)
    val slop = viewConfiguration.touchSlop * 2
    var dragged = false
    edit.lock.held = true // page pan/zoom must not move the page under the finger while selecting
    try {
        drag(down.id) { change ->
            change.consume()
            if (!dragged && (change.position - down.position).getDistance() > slop) dragged = true
            if (dragged) edit.hooks.onUpdate(edit.page, start, fraction(change.position, size))
            edit.onFinger(change.position)
        }
    } finally {
        edit.lock.held = false
    }
    edit.onFinger(null)
    // the lift is ours too: otherwise the tap handler sees a plain tap and drops the selection/box
    currentEvent.changes.forEach { it.consume() }
}

/** Page-fraction points just inside the first and last selected line, used as the fixed/moving ends. */
private class HandleAnchors(val start: Offset, val end: Offset)

private fun anchorsOf(rects: List<MatchRect>): HandleAnchors {
    val first = rects.first()
    val last = rects.last()
    return HandleAnchors(
        Offset(first.left + EDGE_INSET, (first.top + first.bottom) * HALF),
        Offset(last.right - EDGE_INSET, (last.top + last.bottom) * HALF),
    )
}

/** Handle centres (px) just below the start of the first line and the end of the last line. */
private fun handleCenters(rects: List<MatchRect>, w: Float, h: Float, radius: Float): Pair<Offset, Offset> {
    val first = rects.first()
    val last = rects.last()
    return Offset(first.left * w, first.bottom * h + radius) to Offset(last.right * w, last.bottom * h + radius)
}

/**
 * Drag one of the two selection handles to extend/shrink the selection; the other end stays fixed. A touch that
 * misses both handles is left alone (long-press, tap and scroll handle it).
 */
internal suspend fun AwaitPointerEventScope.selectHandleGesture(current: () -> SelectEdit?, zoomOf: () -> Float) {
    // every new touch of this gesture is checked, not only the first: a resting palm/finger must not hide a handle
    do {
        val event = awaitPointerEvent()
        val down = event.changes.firstOrNull { it.changedToDown() }
        if (down != null && grabHandle(down, current, zoomOf)) return
    } while (event.changes.any { it.pressed })
}

/** True when [down] landed on a handle of the current selection and that handle was dragged. */
private suspend fun AwaitPointerEventScope.grabHandle(
    down: PointerInputChange,
    current: () -> SelectEdit?,
    zoomOf: () -> Float,
): Boolean {
    val edit = current() ?: return false
    val rects = edit.hooks.selection?.takeIf { it.page == edit.page }?.rects.orEmpty()
    val moveStart = handleHit(down.position, rects, zoomOf())
    if (moveStart != null) {
        down.consume()
        dragHandle(down, edit, anchorsOf(rects), moveStart)
    }
    return moveStart != null
}

/** true = start handle, false = end handle, null = neither (or no selection). */
private fun AwaitPointerEventScope.handleHit(at: Offset, rects: List<MatchRect>, zoomNow: Float): Boolean? {
    if (rects.isEmpty()) return null
    val zoom = maxOf(zoomNow, MIN_ZOOM)
    val radius = SelectHandleRadius.toPx() / zoom
    val (startAt, endAt) = handleCenters(rects, size.width.toFloat(), size.height.toFloat(), radius)
    val reach = HandleReach.toPx() / zoom
    return when {
        (at - startAt).getDistance() <= reach -> true
        (at - endAt).getDistance() <= reach -> false
        else -> null
    }
}

private suspend fun AwaitPointerEventScope.dragHandle(
    down: PointerInputChange,
    edit: SelectEdit,
    anchors: HandleAnchors,
    moveStart: Boolean,
) {
    val moving = if (moveStart) anchors.start else anchors.end
    val fixed = if (moveStart) anchors.end else anchors.start
    val origin = Offset(moving.x * size.width, moving.y * size.height)
    edit.lock.held = true
    try {
        drag(down.id) { change ->
            change.consume()
            val to = fraction(origin + (change.position - down.position), size)
            if (moveStart) edit.hooks.onUpdate(edit.page, to, fixed) else edit.hooks.onUpdate(edit.page, fixed, to)
            edit.onFinger(change.position)
        }
    } finally {
        edit.lock.held = false
    }
    edit.onFinger(null)
}

/** Selected lines plus two round handles; handle size stays constant on screen at any zoom. */
internal fun DrawScope.drawSelection(rects: List<MatchRect>, zoom: Float) {
    rects.forEach { r ->
        drawRect(
            SelectionFill,
            Offset(r.left * size.width, r.top * size.height),
            Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
        )
    }
    if (rects.isEmpty()) return
    val radius = SelectHandleRadius.toPx() / maxOf(zoom, MIN_ZOOM)
    val (startAt, endAt) = handleCenters(rects, size.width, size.height, radius)
    drawCircle(SelectHandleColor, radius, startAt)
    drawCircle(SelectHandleColor, radius, endAt)
}
