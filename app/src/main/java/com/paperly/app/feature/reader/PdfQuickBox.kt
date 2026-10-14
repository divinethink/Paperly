package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect

private const val QUICK_HEIGHT = 0.03f // about one text line, as a fraction of the page height
private const val QUICK_WIDTH = 0.4f // starting length of the box, as a fraction of the page width
private const val QUICK_MIN_WIDTH = 0.05f
private const val QUICK_HALF = 0.5f
private const val QUICK_SLOP_FACTOR = 2
private val QuickTopPadding = 8.dp
private val QuickSidePadding = 12.dp
private val QuickElevation = 6.dp

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

/** Saves the quick box in [type] and the colour already chosen; the type is remembered like a strip choice. */
internal fun AnnotationController.commitQuick(type: AnnotationType): AnnotationEditor? {
    val chosen = style.value.copy(type = type)
    setStyle(chosen)
    return commitPending(chosen)
}

/** Select hooks plus the image-page quick box (only once the text-layer probe says there is nothing readable). */
@Composable
internal fun AnnotationHooks.withSelect(viewModel: ReaderViewModel, enabled: Boolean): AnnotationHooks {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val controller = viewModel.annotations
    val quick = remember(controller) { QuickHooks(controller::quickStart, controller::cancelPending) }
    val imageOnly = enabled && search.probed && !search.available
    return copy(
        select = rememberSelectHooks(viewModel, enabled),
        pending = pending.copy(quick = quick.takeIf { imageOnly }),
    )
}

/** Highlight | Underline | Strikethrough | Note (+ colour, Cancel) for the quick box; hidden in annotate/Reflow. */
@Composable
internal fun BoxScope.PdfQuickMenu(viewModel: ReaderViewModel, onEditor: (AnnotationEditor) -> Unit) {
    val controller = viewModel.annotations
    val pending by controller.pending.collectAsStateWithLifecycle()
    val annotate by controller.annotateMode.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val style by controller.style.collectAsStateWithLifecycle()
    if (pending == null || annotate || reflow) return
    Surface(
        modifier = Modifier.align(Alignment.TopCenter)
            .padding(top = QuickTopPadding, start = QuickSidePadding, end = QuickSidePadding),
        shape = MaterialTheme.shapes.large,
        tonalElevation = QuickElevation,
        shadowElevation = QuickElevation,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    PDF_STRIP_TYPES.forEach { (type, label) ->
                        TextButton(onClick = { controller.commitQuick(type)?.let(onEditor) }) {
                            Text(stringResource(label))
                        }
                    }
                }
                TextButton(onClick = controller::cancelPending) { Text(stringResource(R.string.action_cancel)) }
            }
            Row(Modifier.fillMaxWidth()) {
                AnnotationColor.entries.forEach { color ->
                    ColorSwatch(color, color == style.color, Modifier.weight(1f)) {
                        controller.setStyle(style.copy(color = color))
                    }
                }
            }
        }
    }
}
