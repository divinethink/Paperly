package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import kotlinx.coroutines.launch

private const val QUICK_HEIGHT = 0.03f // about one text line, as a fraction of the page height
private const val QUICK_WIDTH = 0.4f // starting length of the box, as a fraction of the page width
private const val QUICK_MIN_WIDTH = 0.05f
private const val QUICK_HALF = 0.5f
private const val QUICK_SLOP_FACTOR = 2
private val QuickTopPadding = 8.dp
private val QuickSidePadding = 12.dp
private val QuickElevation = 6.dp
private val QuickShape = RoundedCornerShape(16.dp) // same look as the text-select popup

/**
 * Long-press on a page with no readable text layer (scan, image PDF, Bijoy text) drops a one-line box here; the
 * floating menu then saves it in one tap. Reuses the pending-part box (resize / move / remove) and the annotate style.
 */
data class QuickHooks(val onStart: (Int, MatchRect) -> Unit, val onCancel: () -> Unit)

/**
 * The one-line box for a long-press at ([x], [y]) (page fractions). Without [endX] it is centred on the finger;
 * dragging sideways makes it run from the press point to [endX]. Always inside the page.
 */
internal fun quickBoxRect(x: Float, y: Float, endX: Float?): MatchRect {
    val top = (y - QUICK_HEIGHT * QUICK_HALF).coerceIn(0f, 1f - QUICK_HEIGHT)
    val (left, right) = if (endX == null) {
        val start = (x - QUICK_WIDTH * QUICK_HALF).coerceIn(0f, 1f - QUICK_WIDTH)
        start to start + QUICK_WIDTH
    } else {
        val start = minOf(x, endX).coerceIn(0f, 1f - QUICK_MIN_WIDTH)
        start to maxOf(maxOf(x, endX), start + QUICK_MIN_WIDTH).coerceAtMost(1f)
    }
    return MatchRect(left, top, right, top + QUICK_HEIGHT)
}

/**
 * Long-press makes the box, dragging on stretches it. A move before the long-press fires returns without
 * consuming, so page scroll and pinch-zoom are untouched (same pattern as [textSelectGesture]).
 */
internal suspend fun AwaitPointerEventScope.quickPressGesture(edit: () -> PendingEdit) {
    val down = awaitFirstDown(requireUnconsumed = true) // a grabbed box/handle already consumed its down
    val pressed = awaitLongPressOrCancellation(down.id) ?: return
    val current = edit()
    val onQuick = current.onQuick
    if (size.width <= 0 || size.height <= 0 || onQuick == null) return
    pressed.consume()
    val startX = (down.position.x / size.width).coerceIn(0f, 1f)
    val startY = (down.position.y / size.height).coerceIn(0f, 1f)
    onQuick(quickBoxRect(startX, startY, null))
    current.onFinger(pressed.position)
    val slop = viewConfiguration.touchSlop * QUICK_SLOP_FACTOR
    drag(down.id) { change ->
        change.consume()
        if ((change.position - down.position).getDistance() > slop) {
            onQuick(quickBoxRect(startX, startY, (change.position.x / size.width).coerceIn(0f, 1f)))
        }
        current.onFinger(change.position)
    }
    current.onFinger(null)
    // the lift is ours too: otherwise the tap handler sees a plain tap and drops the selection/box
    currentEvent.changes.forEach { it.consume() }
}

/** Starts (or replaces) the single quick box; a box left on another page is dropped first. */
internal fun AnnotationController.quickStart(page: Int, rect: MatchRect) {
    if (pending.value?.page == page) {
        changePending(0, rect)
    } else {
        cancelPending()
        addPending(page, rect)
    }
}

/** Which quick-box gestures a page needs: [press] = its own long-press (text selection off), [live] = grab the box. */
internal class QuickState(val press: Boolean, val live: Boolean)

/** With text selection on, a press that finds no text reaches the box via `onMiss`, not [QuickState.press]. */
internal fun quickState(edit: PendingEdit, annotate: Boolean, hasSelect: Boolean): QuickState {
    val on = edit.onQuick != null && !annotate
    return QuickState(press = on && !hasSelect, live = on && edit.rects.isNotEmpty())
}

/** A tap that hit nothing drops a quick box (never in annotate mode, where pending parts are the user's work). */
internal fun PendingEdit.cancelQuick(annotate: Boolean) {
    if (!annotate) pending.quick?.onCancel?.invoke()
}

/** A long-press/drag that found no text: drop (or stretch) the quick box there. Not in annotate mode. */
internal fun AnnotationController.quickMiss(page: Int, from: Offset, to: Offset?) {
    if (!annotateMode.value) quickStart(page, quickBoxRect(from.x, from.y, to?.x))
}

/** Saves the quick box in [type] and the colour already chosen; the type is remembered like a strip choice. */
internal fun AnnotationController.commitQuick(type: AnnotationType): AnnotationEditor? {
    val chosen = style.value.copy(type = type)
    setStyle(chosen)
    return commitPending(chosen)
}

/**
 * Select hooks plus the quick box (on after the probe: text-less pages, or a press that hits no text).
 * [onBlankTap] = a tap on empty page space that closed nothing (the Reader toggles its bars).
 */
@Composable
internal fun AnnotationHooks.withSelect(
    viewModel: ReaderViewModel,
    enabled: Boolean,
    onBlankTap: () -> Unit,
): AnnotationHooks {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val controller = viewModel.annotations
    val quick = remember(controller) { QuickHooks(controller::quickStart, controller::cancelPending) }
    val ready = enabled && search.probed
    return copy(
        select = rememberSelectHooks(viewModel, enabled),
        pending = pending.copy(quick = quick.takeIf { ready }, onBlankTap = onBlankTap),
    )
}

/** The text-select popup bar (colour dot, mark icons, Note, Copy) for the quick box; hidden in annotate/Reflow. */
@Composable
internal fun BoxScope.PdfQuickMenu(viewModel: ReaderViewModel, onEditor: (AnnotationEditor) -> Unit) {
    val controller = viewModel.annotations
    val pending by controller.pending.collectAsStateWithLifecycle()
    val annotate by controller.annotateMode.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val style by controller.style.collectAsStateWithLifecycle()
    val page = pending?.page ?: return
    if (annotate || reflow) return
    val copyPage = rememberOcrCopy(viewModel)
    val actions = SelectionActions(
        onColor = { controller.setStyle(style.copy(color = it)) },
        onStroke = { controller.setStyle(style.copy(stroke = it)) },
        onMark = { type -> controller.commitQuick(type)?.let(onEditor) },
        onCopy = { copyPage(page) },
    )
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(
            top = readerEdgePadding().calculateTopPadding() + QuickTopPadding,
            start = QuickSidePadding,
            end = QuickSidePadding,
        ),
        shape = QuickShape,
        tonalElevation = QuickElevation,
        shadowElevation = QuickElevation,
    ) {
        PdfSelectionBar(style, null, actions)
    }
}

/** "Copy" for a page without a text layer: reads the page by OCR (the same engine as Reflow) and copies its text. */
@Composable
private fun rememberOcrCopy(viewModel: ReaderViewModel): (Int) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { page ->
        scope.launch {
            val text = viewModel.reflowOcr.copyPageText(page) {
                Toast.makeText(context, R.string.reader_ocr_downloading, Toast.LENGTH_SHORT).show()
            }
            if (text == null) {
                Toast.makeText(context, R.string.reader_ocr_copy_failed, Toast.LENGTH_LONG).show()
            } else {
                val clip = ClipData.newPlainText("page", text)
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.reader_ocr_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
