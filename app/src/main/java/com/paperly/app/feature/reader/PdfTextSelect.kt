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
import androidx.compose.ui.unit.IntSize
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

    /** Selects between two page-fraction points; a drag over blank space keeps the previous selection. */
    fun update(page: Int, from: Offset, to: Offset) {
        job?.cancel()
        job = scope.launch {
            val picked = engine()?.selectText(page, from.x, from.y, to.x, to.y) ?: return@launch
            selectionState.value = PdfSelection(page, picked.rects, picked.text)
        }
    }

    fun clear() {
        job?.cancel()
        selectionState.value = null
    }
}

/** Selection wiring handed down to each page; null = text selection off (annotate mode, unreadable/Bengali text). */
data class SelectHooks(
    val selection: PdfSelection?,
    val onUpdate: (Int, Offset, Offset) -> Unit,
    val onClear: () -> Unit,
)

/** One page's view of [SelectHooks]; [onFinger] drives the loupe. */
internal class SelectEdit(val page: Int, val hooks: SelectHooks, val onFinger: (Offset?) -> Unit)

/** Text selection is offered only when the document's text layer is readable and not partial (Bengali). */
@Composable
internal fun rememberSelectHooks(viewModel: ReaderViewModel, enabled: Boolean): SelectHooks? {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val selection by viewModel.textSelect.state.collectAsStateWithLifecycle()
    val controller = viewModel.textSelect
    return if (!enabled || !search.available || search.partialMatch) {
        null
    } else {
        remember(selection, controller) { SelectHooks(selection, controller::update, controller::clear) }
    }
}

private fun fraction(p: Offset, size: IntSize) =
    Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))

/**
 * Long-press, then drag: the text between the press point and the finger is selected. A move before the long-press
 * fires returns without consuming, so page scroll and pinch-zoom are untouched.
 */
internal suspend fun AwaitPointerEventScope.textSelectGesture(edit: SelectEdit) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val pressed = awaitLongPressOrCancellation(down.id) ?: return
    if (size.width <= 0 || size.height <= 0) return
    pressed.consume()
    val start = fraction(down.position, size)
    edit.hooks.onClear()
    edit.hooks.onUpdate(edit.page, start, start)
    edit.onFinger(pressed.position)
    drag(down.id) { change ->
        change.consume()
        edit.hooks.onUpdate(edit.page, start, fraction(change.position, size))
        edit.onFinger(change.position)
    }
    edit.onFinger(null)
}

internal fun DrawScope.drawSelection(rects: List<MatchRect>) {
    rects.forEach { r ->
        drawRect(
            SelectionFill,
            Offset(r.left * size.width, r.top * size.height),
            Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
        )
    }
}
