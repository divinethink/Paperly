package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType

private val PopupWidth = 300.dp
private val PopupGap = 8.dp
private val PopupElevation = 6.dp

/** Popup wiring handed to the page area: full-dialog opener, the tapped annotation (null = none) and its setter. */
internal class PopupHooks(
    val onEditor: (AnnotationEditor) -> Unit,
    val tapped: Annotation?,
    val onTap: (Annotation?) -> Unit,
)

/** Both floating menus of the page area: text-selection and tapped-annotation. */
@Composable
internal fun PdfPopups(viewModel: ReaderViewModel, port: SelectionViewport, popup: PopupHooks) {
    PdfSelectionPopup(viewModel, port, popup.onEditor)
    PdfAnnotationPopup(viewModel, port, popup)
}

/**
 * Tap on a saved annotation: colour swatches (not for notes), Edit (the full dialog: type, note text), Delete,
 * Cancel — next to the annotation instead of a dialog. A tap on empty space dismisses it.
 */
@Composable
internal fun PdfAnnotationPopup(viewModel: ReaderViewModel, port: SelectionViewport, popup: PopupHooks) {
    val tapped = popup.tapped ?: return
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    if (reflow) return
    val controller = viewModel.annotations
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { PopupGap.toPx() }
    val where = PdfSelection.single(tapped.page, tapped.rects, "")
    val editor = AnnotationEditor(tapped.page, tapped.rect, tapped)
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .width(PopupWidth)
                .onSizeChanged { menu = it }
                .offset { popupOrigin(where, port, menu, gap, gap) },
            shape = MaterialTheme.shapes.large,
            tonalElevation = PopupElevation,
            shadowElevation = PopupElevation,
        ) {
            AnnotationMenu(tapped, editor, controller, popup)
        }
    }
}

/** Colour swatches, one-tap type icons and thickness (not for notes), then Edit / Delete / Cancel. */
@Composable
private fun AnnotationMenu(
    tapped: Annotation,
    editor: AnnotationEditor,
    controller: AnnotationController,
    popup: PopupHooks,
) {
    Column {
        if (tapped.type != AnnotationType.NOTE) {
            val current = tapped.color ?: AnnotationColor.defaultFor(tapped.type)
            Row(Modifier.fillMaxWidth()) {
                AnnotationColor.entries.forEach { color ->
                    ColorSwatch(color, color == current, Modifier.weight(1f)) {
                        controller.save(editor, AnnotationContent(tapped.type, color, null, tapped.stroke))
                        popup.onTap(null)
                    }
                }
            }
            MarkIconRow(tapped.type, current, Modifier.fillMaxWidth()) { type ->
                controller.save(editor, AnnotationContent(type, tapped.color, null, tapped.stroke))
                popup.onTap(null)
            }
            if (AnnotationStroke.appliesTo(tapped.type)) {
                StrokeRow(tapped.stroke ?: AnnotationStroke.MEDIUM, current, Modifier.fillMaxWidth()) { stroke ->
                    controller.save(editor, AnnotationContent(tapped.type, tapped.color, null, stroke))
                    popup.onTap(null)
                }
            }
        }
        Row {
            TextButton(
                onClick = {
                    popup.onEditor(editor)
                    popup.onTap(null)
                },
            ) { Text(stringResource(R.string.reader_annotation_edit)) }
            TextButton(
                onClick = {
                    controller.delete(tapped.id)
                    popup.onTap(null)
                },
            ) { Text(stringResource(R.string.reader_annotation_delete)) }
            TextButton(onClick = { popup.onTap(null) }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
