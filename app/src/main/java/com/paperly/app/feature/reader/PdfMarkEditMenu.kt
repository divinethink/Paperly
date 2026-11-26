package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.lookFor

private val EditGap = 36.dp // the bar sits about two text lines above the box (clear of the round handles)
private const val BELOW_FACTOR = 0.5f // below the box there are no handle dots above the bar, so less room is needed

/**
 * The floating bar of a picked saved mark (tap on it): colour dot + panel, the mark-type icons, Note, and Delete.
 * Same bar as the text-selection menu; changes apply to the mark itself. Hidden in annotate mode and Reflow.
 */
@Composable
internal fun PdfMarkEditMenu(
    viewModel: ReaderViewModel,
    port: SelectionViewport,
    onEditor: (AnnotationEditor) -> Unit,
) {
    val controller = viewModel.annotations
    val editing by controller.editing.collectAsStateWithLifecycle()
    val pending by controller.pending.collectAsStateWithLifecycle()
    val items by controller.items.collectAsStateWithLifecycle()
    val tools by controller.tools.collectAsStateWithLifecycle()
    val annotate by controller.annotateMode.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val picked = editing ?: return
    val mark = items.firstOrNull { it.id == picked.id }
    // the mark vanished (deleted from another place): drop the box with it
    LaunchedEffect(mark == null) { if (mark == null) controller.cancelPending() }
    val frame = pending?.takeIf { it.editing }?.rects?.singleOrNull()
    val hidden = annotate || reflow
    if (mark == null || frame == null || hidden) return
    val gap = with(LocalDensity.current) { EditGap.toPx() }
    val own = AnnotateStyle(
        mark.type,
        mark.color ?: AnnotationColor.defaultFor(mark.type),
        mark.stroke ?: AnnotationStroke.MEDIUM,
    )
    val where = PdfSelection.single(mark.page, listOf(frame), "")
    SelectionToolbar(own, mark.type, markEditActions(controller, mark, tools, onEditor)) { bar, _ ->
        popupPlacement(where, port, bar, gap, gap * BELOW_FACTOR)
    }
}

private fun markEditActions(
    controller: AnnotationController,
    mark: Annotation,
    tools: Map<AnnotationType, AnnotateStyle>,
    onEditor: (AnnotationEditor) -> Unit,
): SelectionActions {
    val look = tools.lookFor(mark.type)
    return SelectionActions(
        onColor = { color ->
            controller.setStyle(look.copy(color = color))
            controller.restyleMark(mark.id) { it.copy(color = color) }
        },
        onStroke = { stroke ->
            controller.setStyle(look.copy(stroke = stroke))
            controller.restyleMark(mark.id) { it.copy(stroke = stroke) }
        },
        onMark = { type ->
            if (type == AnnotationType.NOTE) {
                onEditor(AnnotationEditor(mark.page, mark.rect, mark)) // the dialog takes the note text
            } else {
                val next = tools.lookFor(type) // each tool keeps its own colour and thickness
                controller.setStyle(next)
                controller.restyleMark(mark.id) { it.copy(type = type, color = next.color, stroke = next.stroke) }
            }
        },
        onCopy = { controller.delete(mark.id) },
        types = PDF_MARK_TYPES,
        lastLabel = R.string.reader_annotation_delete,
    )
}
