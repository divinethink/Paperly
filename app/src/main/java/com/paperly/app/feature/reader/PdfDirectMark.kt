package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect

private const val DIRECT_HEIGHT = 0.03f // about one text line, as a fraction of the page height
private const val DIRECT_MIN_WIDTH = 0.02f // shorter drags are accidental
private const val DIRECT_CENTRE = 0.5f

/**
 * The mark a one-finger drag from [from] to [to] makes on an image page: a one-line box whose underline /
 * strikethrough / highlight sits exactly at the finger's start height, so a wobbly finger still gives a straight
 * line. A Highlight dragged down further than one line becomes the dragged area instead.
 */
internal fun directMarkRect(type: AnnotationType, from: Offset, to: Offset, size: IntSize): MatchRect? {
    if (size.width <= 0 || size.height <= 0) return null
    val area = dragRect(from, to, size)
    val tall = area != null && area.bottom - area.top >= DIRECT_HEIGHT
    if (tall && (type == AnnotationType.HIGHLIGHT || type == AnnotationType.NOTE)) return area
    val at = when (type) {
        AnnotationType.STRIKETHROUGH, AnnotationType.HIGHLIGHT, AnnotationType.NOTE -> DIRECT_CENTRE
        else -> UNDERLINE_AT
    }
    val y = (from.y / size.height).coerceIn(0f, 1f)
    val top = (y - DIRECT_HEIGHT * at).coerceIn(0f, 1f - DIRECT_HEIGHT)
    val left = (minOf(from.x, to.x) / size.width).coerceIn(0f, 1f)
    val right = (maxOf(from.x, to.x) / size.width).coerceIn(0f, 1f)
    return MatchRect(left, top, right, top + DIRECT_HEIGHT).takeIf { right - left >= DIRECT_MIN_WIDTH }
}

/** Annotate mode on an image page: drag = the mark follows the finger (draft + loupe), lift = saved. */
internal suspend fun AwaitPointerEventScope.directMarkGesture(edit: () -> PendingEdit) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val current = edit()
    val type = current.pending.directType ?: return
    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return
    var end = slop.position
    current.onDraft(directMarkRect(type, down.position, end, size))
    current.onFinger(end)
    val finished = drag(slop.id) { change ->
        change.consume()
        end = change.position
        current.onDraft(directMarkRect(type, down.position, end, size))
        current.onFinger(end)
    }
    current.onDraft(null)
    current.onFinger(null)
    if (finished) directMarkRect(type, down.position, end, size)?.let(current.onCreate)
}

/** Annotate-mode touch of one page: direct drawing on image pages, the pending-part boxes everywhere else. */
internal suspend fun AwaitPointerEventScope.annotateGesture(
    reachBase: Float,
    zoomOf: () -> Float,
    edit: () -> PendingEdit,
) {
    if (edit().pending.directType != null) directMarkGesture(edit) else pendingGesture(reachBase, zoomOf, edit = edit)
}

/**
 * Image / scan PDFs (no readable text layer) in annotate mode: a drag saves the mark at once in the strip's type
 * and colour - no pending box, handles or Done. A Note still opens its text dialog.
 */
@Composable
internal fun AnnotationHooks.withDirectMark(
    viewModel: ReaderViewModel,
    enabled: Boolean,
    openEditor: (AnnotationEditor) -> Unit,
): AnnotationHooks {
    val controller = viewModel.annotations
    val style by controller.style.collectAsStateWithLifecycle()
    if (!enabled) return this
    return copy(
        onCreate = { page, rect ->
            controller.quickStart(page, rect)
            controller.commitQuick(style.type)?.let(openEditor)
        },
        pending = pending.copy(directType = style.type),
    )
}
