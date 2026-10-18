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
import com.paperly.app.feature.reader.SelectDebugLog.caller
import com.paperly.app.feature.reader.SelectDebugLog.f
import com.paperly.app.feature.reader.SelectDebugLog.log
import com.paperly.app.feature.reader.SelectDebugLog.pt
import com.paperly.app.feature.reader.SelectDebugLog.rs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SelectionFill = Color(0x5542A5F5)
private val SelectHandleColor = Color(0xFF1E88E5)
private val SelectHandleRadius = 12.dp // big drop-shaped handle, easy to grab (Xodo-like)
private val SelectHandleReach = 36.dp // touch reach around a handle
private const val EDGE_INSET = 0.002f // anchors sit just inside the line ends, so the end characters stay selected
private const val HALF = 0.5f
private const val MAX_SELECT_PAGES = 10 // a drag across more pages is ignored (keeps the last selection)
private val PAGE_TOP_LEFT = Offset(0f, 0f)
private val PAGE_BOTTOM_RIGHT = Offset(1f, 1f)

/** The picked lines [rects] (page fractions) of one [page], with their plain [text]. */
data class SelectionPart(val page: Int, val rects: List<MatchRect>, val text: String)

/**
 * Text picked over one or more pages: one [SelectionPart] per page, in reading order (first = where the selection
 * starts, last = where it ends). Not persisted. [markIds] = the marks already saved on exactly these parts (one per
 * page, same order); any change of the selection drops them (the next mark is a new one).
 */
data class PdfSelection(val parts: List<SelectionPart>, val markIds: List<String> = emptyList()) {
    /** All parts' text joined in reading order (what Copy puts on the clipboard). */
    val text: String get() = parts.joinToString("\n") { it.text }

    /** Selected lines on [page] (empty when the selection does not touch it). */
    fun rectsOn(page: Int): List<MatchRect> = parts.firstOrNull { it.page == page }?.rects.orEmpty()

    /** True when [page] holds the start / the end of the selection: only those pages show a handle. */
    fun startsOn(page: Int): Boolean = parts.firstOrNull()?.page == page

    fun endsOn(page: Int): Boolean = parts.lastOrNull()?.page == page

    companion object {
        /** The usual one-page selection. */
        fun single(page: Int, rects: List<MatchRect>, text: String) =
            PdfSelection(listOf(SelectionPart(page, rects, text)))
    }
}

/** Long-press + drag text selection for text-layer PDF pages (P3-K). Latest drag position wins. */
class PdfTextSelectController(
    private val scope: CoroutineScope,
    private val engine: () -> ReaderEngine?,
) {
    private val selectionState = MutableStateFlow<PdfSelection?>(null)
    val state: StateFlow<PdfSelection?> = selectionState.asStateFlow()
    private var job: Job? = null // long-press word pick
    private var worker: Job? = null // drains [latest]; one engine call at a time
    private var latest: UpdateRequest? = null
    private val draggingState = MutableStateFlow(false)

    /** True while a finger is down on a selection gesture (long-press or handle drag): the mark panel stays hidden. */
    val dragging: StateFlow<Boolean> = draggingState.asStateFlow()

    fun setDragging(value: Boolean) {
        draggingState.value = value
    }

    /** Called when a press/drag lands where there is no text (scan, watermark-only layer): the quick box takes over. */
    var onMiss: ((Int, Offset, Offset?) -> Unit)? = null

    /**
     * Selects between two page-fraction points, on one page or across pages ([from] and [to] may sit on different
     * pages, in either order); a drag over blank space keeps the previous selection. Calls never cancel each other
     * (a finger moving faster than the engine would starve every call): the newest request waits for the running one,
     * older unstarted ones are dropped. [pin] keeps the fixed end of a handle drag in place.
     */
    fun update(from: PagePoint, to: PagePoint, pin: SelectionPin? = null) {
        job?.cancel() // a slow word pick must not land on top of the drag
        val pins = "${pin?.first != null}/${pin?.last != null}"
        log("REQ p${from.page}${pt(from.point)}->p${to.page}${pt(to.point)} pin=$pins")
        latest = UpdateRequest(from, to, pin)
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val request = latest ?: break
                latest = null
                apply(request)
            }
        }
    }

    private suspend fun apply(r: UpdateRequest) {
        val pdf = engine() ?: return
        val samePage = r.from.page == r.to.page
        val parts = if (samePage) pickOnePage(pdf, r) else pickAcross(pdf, r)
        log("PICK same=$samePage ${parts?.joinToString { "p${it.page} ${rs(it.rects)}" }}")
        if (parts != null) {
            selectionState.value = PdfSelection(parts)
        } else if (selectionState.value == null && (samePage || latest == null)) {
            // (a cross-page pick dropped for a newer waiting request is not a miss)
            onMiss?.invoke(r.from.page, r.from.point, r.to.point.takeIf { samePage })
        }
    }

    private suspend fun pickOnePage(pdf: ReaderEngine, r: UpdateRequest): List<SelectionPart>? {
        val (page, from) = r.from
        val to = r.to.point
        val raw = pdf.selectText(page, from.x, from.y, to.x, to.y)
        log("RAW ${pt(from)}->${pt(to)} ${raw?.let { rs(it.rects) }} len=${raw?.text?.length}")
        val picked = raw?.takeIf { isNear(it.rects, from) } ?: return null
        // the pin belongs to the fixed end: only while the picked rects really run from it towards the moving end
        val pin = r.pin?.takeIf { pickForward(picked.rects, from, to) }
        return listOf(SelectionPart(page, pinEnds(clampEnds(picked.rects, from, to), pin), picked.text))
    }

    /**
     * One engine call per page, in reading order: the first page from its start point to the page end, middle pages
     * whole, the last page from its top to the end point. The press ([UpdateRequest.from]) must be on text; a page
     * without text is skipped. Null = nothing to change (no text at the press, too many pages, or a newer request
     * is already waiting).
     */
    private suspend fun pickAcross(pdf: ReaderEngine, r: UpdateRequest): List<SelectionPart>? {
        val forward = r.from.page < r.to.page
        val first = if (forward) r.from else r.to
        val last = if (forward) r.to else r.from
        val parts = ArrayList<SelectionPart>()
        var valid = last.page - first.page + 1 <= MAX_SELECT_PAGES
        var page = first.page
        // stops at once when a newer request is waiting (it redoes this one) or the press is not on text
        while (valid && latest == null && page <= last.page) {
            val from = if (page == first.page) first.point else PAGE_TOP_LEFT
            val to = if (page == last.page) last.point else PAGE_BOTTOM_RIGHT
            val picked = pdf.selectText(page, from.x, from.y, to.x, to.y)?.takeIf { it.rects.isNotEmpty() }
            valid = page != r.from.page || (picked != null && isNear(picked.rects, r.from.point))
            if (valid && picked != null) {
                parts += SelectionPart(page, pageEnds(picked.rects, page, first, last, r.pin), picked.text)
            }
            page++
        }
        return parts.takeIf { valid && latest == null && it.isNotEmpty() }
    }

    /** Only the first page trims/pins its start, only the last page its end; middle pages stay whole. */
    private fun pageEnds(
        rects: List<MatchRect>,
        page: Int,
        first: PagePoint,
        last: PagePoint,
        pin: SelectionPin?,
    ): List<MatchRect> {
        var out = rects
        if (page == first.page) out = pinEnds(trimStart(out, first.point), pin?.copy(last = null))
        if (page == last.page) out = pinEnds(trimEnd(out, last.point), pin?.let { SelectionPin(last = it.last) })
        return out
    }

    /** Long-press without drag: selects the word under [at] (page fractions). */
    fun selectWord(page: Int, at: Offset) {
        stopPending()
        job = scope.launch {
            val pdf = engine() ?: return@launch
            val picked = pickWordAt(pdf, page, at)?.takeIf { isNear(it.rects, at) }
            log("WORD p$page ${pt(at)} -> ${picked?.let { rs(it.rects) }}")
            if (picked != null) {
                selectionState.value = PdfSelection.single(page, picked.rects, picked.text.trim())
            } else {
                onMiss?.invoke(page, at, null)
            }
        }
    }

    /** Ties [ids] to [expected] while that selection is still the current one (a moved handle means a new mark). */
    fun linkMark(expected: PdfSelection, ids: List<String>) {
        if (selectionState.value == expected) selectionState.value = expected.copy(markIds = ids)
    }

    private fun stopPending() {
        job?.cancel()
        worker?.cancel()
        latest = null
    }

    fun clear() {
        log("CLEAR <${caller()}")
        stopPending()
        selectionState.value = null
        draggingState.value = false
    }
}

private class UpdateRequest(val from: PagePoint, val to: PagePoint, val pin: SelectionPin?)

/** Selection wiring handed down to each page; null = text selection off (annotate mode, no readable text layer). */
data class SelectHooks(
    val selection: PdfSelection?,
    val onUpdate: (PagePoint, PagePoint, SelectionPin?) -> Unit,
    val onWord: (Int, Offset) -> Unit,
    val onClear: () -> Unit,
    val onDragging: (Boolean) -> Unit = {},
)

/** One page's view of [SelectHooks]; [onFinger] drives the loupe. */
internal class SelectEdit(
    val page: Int,
    val hooks: SelectHooks,
    val lock: GestureLock,
    val edge: EdgeScroll,
    val locator: PageLocator?,
    val onFinger: (Offset?) -> Unit,
) {
    /** Page and page-fraction point under [local] (this page's own px, may lie on a neighbouring page). */
    fun pointAt(local: Offset, size: IntSize): PagePoint =
        locator?.locate(page, local.y, local.x / size.width) ?: PagePoint(page, fraction(local, size))
}

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
            SelectHooks(
                selection,
                controller::update,
                controller::selectWord,
                controller::clear,
                controller::setDragging,
            )
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
    log("LP down p${edit.page} ${pt(down.position)}")
    val pressed = awaitLongPressOrCancellation(down.id) ?: run {
        log("LP cancelled")
        return
    }
    if (size.width <= 0 || size.height <= 0) return
    pressed.consume()
    val start = fraction(down.position, size)
    log("LP FIRED p${edit.page} ${pt(start)} hadSelection=${edit.hooks.selection != null}")
    edit.hooks.onClear()
    edit.hooks.onWord(edit.page, start)
    edit.onFinger(pressed.position)
    val slop = viewConfiguration.touchSlop * 2
    var dragged = false
    var latest = pressed.position
    val sz = size
    val startPoint = PagePoint(edit.page, start)
    // the page scrolls under a waiting finger near the screen edge: re-pick the end point after every step
    edit.edge.onShift = { dy ->
        latest = latest.copy(y = latest.y + dy)
        if (dragged) edit.hooks.onUpdate(startPoint, edit.pointAt(latest, sz), null)
        edit.onFinger(latest)
    }
    edit.lock.held = true // page pan/zoom must not move the page under the finger while selecting
    var finished = false
    try {
        finished = drag(down.id) { change ->
            change.consume()
            latest = change.position
            if (!dragged && (latest - down.position).getDistance() > slop) dragged = true
            if (dragged) edit.hooks.onUpdate(startPoint, edit.pointAt(latest, sz), null)
            edit.onFinger(latest)
        }
    } finally {
        log("LP end dragged=$dragged finished=$finished")
        edit.edge.onShift = null
        edit.lock.held = false
        edit.onFinger(null)
    }
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

/** Handle centres (px) just below and outside the start of the first line and the end of the last line. */
private fun handleCenters(rects: List<MatchRect>, w: Float, h: Float, radius: Float): Pair<Offset, Offset> {
    val first = rects.first()
    val last = rects.last()
    // Pushed outward by one radius so the two handles never sit on top of each other (a one-word selection).
    // Kept inside the page: text at a page edge must not push a handle off screen where it cannot be grabbed.
    val maxX = (w - radius).coerceAtLeast(radius)
    return Offset((first.left * w - radius).coerceIn(radius, maxX), first.bottom * h + radius) to
        Offset((last.right * w + radius).coerceIn(radius, maxX), last.bottom * h + radius)
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
    val selection = edit.hooks.selection ?: return false
    val rects = selection.rectsOn(edit.page)
    // only the ends of the whole selection carry a handle: the first part's start, the last part's end
    val hasStart = selection.startsOn(edit.page)
    val moveStart = handleHit(down.position, rects, zoomOf(), hasStart, selection.endsOn(edit.page))
    if (moveStart != null) {
        down.consume()
        dragHandle(down, edit, selection, moveStart)
    }
    return moveStart != null
}

/** true = start handle, false = end handle, null = neither (or no selection). */
private fun AwaitPointerEventScope.handleHit(
    at: Offset,
    rects: List<MatchRect>,
    zoomNow: Float,
    hasStart: Boolean,
    hasEnd: Boolean,
): Boolean? {
    if (rects.isEmpty() || !(hasStart || hasEnd)) return null
    val zoom = maxOf(zoomNow, MIN_ZOOM)
    val radius = SelectHandleRadius.toPx() / zoom
    val (startAt, endAt) = handleCenters(rects, size.width.toFloat(), size.height.toFloat(), radius)
    val reach = SelectHandleReach.toPx() / zoom
    val toStart = if (hasStart) (at - startAt).getDistance() else Float.MAX_VALUE
    val toEnd = if (hasEnd) (at - endAt).getDistance() else Float.MAX_VALUE
    // the nearer handle wins: on a short selection the two reach areas overlap and the left one must not steal it
    val hit = if (minOf(toStart, toEnd) > reach) null else toStart <= toEnd
    val side = hit?.let { if (it) "start" else "end" }
    log("HIT ${pt(at)} toStart=${f(toStart)} toEnd=${f(toEnd)} reach=${f(reach)} -> $side")
    return hit
}

private suspend fun AwaitPointerEventScope.dragHandle(
    down: PointerInputChange,
    edit: SelectEdit,
    selection: PdfSelection,
    moveStart: Boolean,
) {
    val firstPart = selection.parts.first()
    val lastPart = selection.parts.last()
    val startAnchor = anchorsOf(firstPart.rects).start
    val endAnchor = anchorsOf(lastPart.rects).end
    val moving = if (moveStart) startAnchor else endAnchor
    // the fixed end keeps its page: it can lie on another page than the finger
    val fixed = if (moveStart) PagePoint(lastPart.page, endAnchor) else PagePoint(firstPart.page, startAnchor)
    val grabbedFirst = firstPart.rects.first()
    val grabbedLast = lastPart.rects.last()
    val origin = Offset(moving.x * size.width, moving.y * size.height)
    val sz = size
    var latest = down.position
    log("DRAG start moveStart=$moveStart fixed=p${fixed.page}${pt(fixed.point)} origin=${pt(origin)}")
    fun push() {
        val to = edit.pointAt(origin + (latest - down.position), sz)
        // the fixed end keeps its grabbed line rect while the moving end stays on its far side
        val pin = if (moveStart) {
            SelectionPin(last = grabbedLast.takeIf { pinnable(to, fixed) })
        } else {
            SelectionPin(first = grabbedFirst.takeIf { pinnable(fixed, to) })
        }
        val (from, end) = if (moveStart) to to fixed else fixed to to
        log("PUSH to=p${to.page}${pt(to.point)} pin=${pin.first != null}/${pin.last != null}")
        edit.hooks.onUpdate(from, end, pin)
        edit.onFinger(latest)
    }
    // the page scrolls under a waiting finger near the screen edge: re-pick the moving end after every step
    edit.edge.onShift = { dy ->
        latest = latest.copy(y = latest.y + dy)
        push()
    }
    edit.lock.held = true
    var finished = false
    try {
        finished = drag(down.id) { change ->
            change.consume()
            latest = change.position
            push()
        }
    } finally {
        log("DRAG end finished=$finished")
        edit.edge.onShift = null
        edit.lock.held = false
        edit.onFinger(null)
    }
    // the lift is ours too: otherwise the tap handler sees a plain tap and drops the selection
    currentEvent.changes.forEach { it.consume() }
}

/** Selected lines plus the round handles (only at the ends of the whole selection); constant size at any zoom. */
internal fun DrawScope.drawSelection(
    rects: List<MatchRect>,
    zoom: Float,
    startHandle: Boolean = true,
    endHandle: Boolean = true,
) {
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
    // drop shape: a circle whose corner nearest the text is square (start: top-right, end: top-left)
    val corner = Size(radius, radius)
    if (startHandle) {
        drawCircle(SelectHandleColor, radius, startAt)
        drawRect(SelectHandleColor, Offset(startAt.x, startAt.y - radius), corner)
    }
    if (endHandle) {
        drawCircle(SelectHandleColor, radius, endAt)
        drawRect(SelectHandleColor, Offset(endAt.x - radius, endAt.y - radius), corner)
    }
}
