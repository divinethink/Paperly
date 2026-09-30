package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect

private val HighlightFill = Color(0x66FFB300)
private val NoteFill = Color(0x3342A5F5)
private val NoteStroke = Color(0xFF1E88E5)
private val LineColor = Color(0xFFD32F2F)
private val DraftFill = Color(0x3300796B)
private val LineWidth = 2.dp
private const val MIN_SIDE = 0.01f // smaller drags (fraction of the page) are treated as accidental

/** Page-level annotation wiring handed down to each page. [items] is keyed by zero-based page. */
data class AnnotationHooks(
    val items: Map<Int, List<Annotation>>,
    val onCreate: (Int, MatchRect) -> Unit,
    val onTap: (Annotation) -> Unit,
)

/** Drag from [a] to [b] inside a page of [size] px -> normalised page-fraction rect, or null if degenerate. */
internal fun dragRect(a: Offset, b: Offset, size: IntSize): MatchRect? {
    if (size.width <= 0 || size.height <= 0) return null
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val rect = MatchRect(
        left = (minOf(a.x, b.x) / w).coerceIn(0f, 1f),
        top = (minOf(a.y, b.y) / h).coerceIn(0f, 1f),
        right = (maxOf(a.x, b.x) / w).coerceIn(0f, 1f),
        bottom = (maxOf(a.y, b.y) / h).coerceIn(0f, 1f),
    )
    return rect.takeIf { it.right - it.left >= MIN_SIDE && it.bottom - it.top >= MIN_SIDE }
}

/** Long-press + drag draws a new area ([onDraft] previews it); a plain tap on an existing annotation edits it. */
@Composable
fun Modifier.annotationGestures(
    annotations: List<Annotation>,
    onDraft: (MatchRect?) -> Unit,
    onCreate: (MatchRect) -> Unit,
    onTap: (Annotation) -> Unit,
): Modifier {
    val draft by rememberUpdatedState(onDraft)
    val create by rememberUpdatedState(onCreate)
    val tap by rememberUpdatedState(onTap)
    val haptic = LocalHapticFeedback.current
    return this
        .pointerInput(annotations) {
            detectTapGestures { pos ->
                val fx = pos.x / size.width
                val fy = pos.y / size.height
                annotations.lastOrNull { fx in it.rect.left..it.rect.right && fy in it.rect.top..it.rect.bottom }
                    ?.let { tap(it) }
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                selectArea(
                    onDraft = { draft(it) },
                    onCreate = { create(it) },
                    onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
            }
        }
}

/**
 * Hold still for the long-press timeout, then drag to size the area. Tracked in the Initial pass and consumed there,
 * so no ancestor scroll/zoom container can take the gesture over. Moving or a second finger before the timeout aborts.
 */
private suspend fun AwaitPointerEventScope.selectArea(
    onDraft: (MatchRect?) -> Unit,
    onCreate: (MatchRect) -> Unit,
    onLongPress: () -> Unit,
) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var aborted = false
    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (!aborted) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.takeIf { it.size == 1 }?.firstOrNull { it.id == down.id }
            aborted = change == null || !change.pressed ||
                (change.position - down.position).getDistance() > viewConfiguration.touchSlop * 2
        }
    } == null
    if (!longPressed) return
    onLongPress()
    var end = down.position
    onDraft(dragRect(down.position, end, size))
    var live = true
    while (live) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null) {
            live = false
        } else {
            change.consume()
            end = change.position
            live = change.pressed
            if (live) onDraft(dragRect(down.position, end, size))
        }
    }
    onDraft(null)
    dragRect(down.position, end, size)?.let(onCreate)
}

fun DrawScope.drawAnnotations(items: List<Annotation>) {
    val lineWidth = LineWidth.toPx()
    items.forEach { a ->
        val topLeft = Offset(a.rect.left * size.width, a.rect.top * size.height)
        val area = Size((a.rect.right - a.rect.left) * size.width, (a.rect.bottom - a.rect.top) * size.height)
        val lineY = if (a.type == AnnotationType.STRIKETHROUGH) topLeft.y + area.height / 2 else topLeft.y + area.height
        when (a.type) {
            AnnotationType.HIGHLIGHT -> drawRect(HighlightFill, topLeft, area)
            AnnotationType.NOTE -> {
                drawRect(NoteFill, topLeft, area)
                drawRect(NoteStroke, topLeft, area, style = Stroke(lineWidth))
            }
            else -> drawLine(LineColor, Offset(topLeft.x, lineY), Offset(topLeft.x + area.width, lineY), lineWidth)
        }
    }
}

fun DrawScope.drawDraft(rect: MatchRect) {
    val topLeft = Offset(rect.left * size.width, rect.top * size.height)
    drawRect(DraftFill, topLeft, Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height))
}

/** Shows the dialog for [editor] (null = hidden); every exit path calls [onClose]. */
@Composable
fun AnnotationEditorHost(editor: AnnotationEditor?, controller: AnnotationController, onClose: () -> Unit) {
    val current = editor ?: return
    var type by remember(current) { mutableStateOf(current.existing?.type ?: AnnotationType.HIGHLIGHT) }
    var note by remember(current) { mutableStateOf(current.existing?.noteText.orEmpty()) }
    val isNote = type == AnnotationType.NOTE
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            val isNew = current.existing == null
            Text(stringResource(if (isNew) R.string.reader_annotation_new else R.string.reader_annotation_edit))
        },
        text = {
            Column {
                AnnotationType.entries.forEach { option ->
                    TypeOption(option, selected = option == type) { type = option }
                }
                if (isNote) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.reader_annotation_note_hint)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isNote || note.isNotBlank(),
                onClick = {
                    controller.save(current, type, note.takeIf { isNote })
                    onClose()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                current.existing?.let { existing ->
                    TextButton(
                        onClick = {
                            controller.delete(existing.id)
                            onClose()
                        },
                    ) { Text(stringResource(R.string.reader_annotation_delete)) }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun TypeOption(option: AnnotationType, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(typeLabel(option)), Modifier.padding(start = 8.dp))
    }
}

private fun typeLabel(type: AnnotationType): Int = when (type) {
    AnnotationType.HIGHLIGHT -> R.string.reader_annotation_highlight
    AnnotationType.UNDERLINE -> R.string.reader_annotation_underline
    AnnotationType.STRIKETHROUGH -> R.string.reader_annotation_strikethrough
    AnnotationType.NOTE -> R.string.reader_annotation_note
}
