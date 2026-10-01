package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect

private val NoteFill = Color(0x3342A5F5)
private val NoteStroke = Color(0xFF1E88E5)
private val DraftFill = Color(0x3300796B)
private val LineWidth = 1.5.dp // on-screen thickness; drawAnnotations divides by the zoom so it stays constant
private const val HIGHLIGHT_ALPHA = 0.4f
private const val MIN_SIDE = 0.01f // smaller drags (fraction of the page) are treated as accidental

/** Current page-list zoom, read lazily in the draw phase so pinching only redraws (never recomposes) the pages. */
internal val LocalPageZoom = staticCompositionLocalOf<() -> Float> { { MIN_ZOOM } }

/** Page-level annotation wiring handed down to each page. [items] is keyed by zero-based page. */
data class AnnotationHooks(
    val items: Map<Int, List<Annotation>>,
    val annotate: Boolean,
    val onCreate: (Int, MatchRect) -> Unit,
    val onTap: (Annotation) -> Unit,
    val pending: PendingHooks,
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

/**
 * In [annotate] mode a one-finger drag draws a new part or moves/resizes/removes a pending one ([edit]); a plain tap
 * on an existing annotation edits it (any mode). Long-press was dropped: it failed twice on-device (Checklist
 * Decision Log).
 */
@Composable
internal fun Modifier.annotationGestures(
    annotations: List<Annotation>,
    annotate: Boolean,
    edit: PendingEdit,
    onTap: (Annotation) -> Unit,
): Modifier {
    val current by rememberUpdatedState(edit)
    val tap by rememberUpdatedState(onTap)
    val zoomOf = LocalPageZoom.current
    val reachBase = with(LocalDensity.current) { HandleReach.toPx() }
    return this
        .pointerInput(annotations) {
            detectTapGestures { pos ->
                val fx = pos.x / size.width
                val fy = pos.y / size.height
                annotations.lastOrNull { a ->
                    a.rects.any { fx in it.left..it.right && fy in it.top..it.bottom }
                }?.let { tap(it) }
            }
        }
        .pointerInput(annotate) {
            if (annotate) awaitEachGesture { pendingGesture(reachBase, zoomOf) { current } }
        }
}

fun DrawScope.drawAnnotations(items: List<Annotation>, zoom: Float) {
    val lineWidth = LineWidth.toPx() / maxOf(zoom, MIN_ZOOM)
    items.forEach { a ->
        val topLeft = Offset(a.rect.left * size.width, a.rect.top * size.height)
        val area = Size((a.rect.right - a.rect.left) * size.width, (a.rect.bottom - a.rect.top) * size.height)
        val lineY = if (a.type == AnnotationType.STRIKETHROUGH) topLeft.y + area.height / 2 else topLeft.y + area.height
        val base = (a.color ?: AnnotationColor.defaultFor(a.type)).argb()
        when (a.type) {
            AnnotationType.HIGHLIGHT -> drawRect(base.copy(alpha = HIGHLIGHT_ALPHA), topLeft, area)
            AnnotationType.NOTE -> {
                drawRect(NoteFill, topLeft, area)
                drawRect(NoteStroke, topLeft, area, style = Stroke(lineWidth))
            }
            else -> drawLine(base, Offset(topLeft.x, lineY), Offset(topLeft.x + area.width, lineY), lineWidth)
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
    AnnotationEditorDialog(
        key = current,
        existing = current.existing,
        initialType = controller.style.value.type,
        callbacks = EditorCallbacks({ controller.save(current, it) }, controller::delete, onClose),
    )
}

/** Dialog exits bundled, so the dialog signature stays within the detekt parameter limit. */
internal class EditorCallbacks(
    val onSave: (AnnotationContent) -> Unit,
    val onDelete: (String) -> Unit,
    val onClose: () -> Unit,
)

/** Shared by PDF and EPUB: [key] resets the fields when a different annotation is edited. */
@Composable
internal fun AnnotationEditorDialog(
    key: Any,
    existing: Annotation?,
    initialType: AnnotationType,
    callbacks: EditorCallbacks,
) {
    val onSave = callbacks.onSave
    val onDelete = callbacks.onDelete
    val onClose = callbacks.onClose
    var type by remember(key) { mutableStateOf(existing?.type ?: initialType) }
    var color by remember(key) { mutableStateOf(existing?.color) }
    var note by remember(key) { mutableStateOf(existing?.noteText.orEmpty()) }
    val isNote = type == AnnotationType.NOTE
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            val isNew = existing == null
            Text(stringResource(if (isNew) R.string.reader_annotation_new else R.string.reader_annotation_edit))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AnnotationType.entries.forEach { option ->
                    TypeOption(option, selected = option == type) { type = option }
                }
                if (!isNote) {
                    ColorPicker(selected = color ?: AnnotationColor.defaultFor(type)) { color = it }
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
                    onSave(AnnotationContent(type, color.takeIf { !isNote }, note.takeIf { isNote }))
                    onClose()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                existing?.let { saved ->
                    TextButton(
                        onClick = {
                            onDelete(saved.id)
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
