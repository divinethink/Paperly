package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation

private val PopupWidth = 300.dp

/**
 * Tap on a saved mark: colours and Highlight/Underline (not for notes) change it at once; Edit opens the full dialog,
 * Delete removes it, Cancel (or a tap elsewhere) closes the popup. Same box as the navigator.
 */
@Composable
internal fun EpubAnnotationPopup(annotations: EpubAnnotationController) {
    val tap by annotations.tapped.collectAsStateWithLifecycle()
    val items by annotations.items.collectAsStateWithLifecycle()
    val shown = tap ?: return
    val item = items.firstOrNull { it.id == shown.id } ?: return
    PopupBox(shown.anchor, Modifier.width(PopupWidth)) { TappedMenu(item, annotations) }
}

@Composable
private fun TappedMenu(item: EpubAnnotation, annotations: EpubAnnotationController) {
    fun change(type: AnnotationType, color: AnnotationColor?, stroke: AnnotationStroke? = item.stroke) {
        annotations.save(EpubAnnotationEditor(null, item), AnnotationContent(type, color, item.noteText, stroke))
        annotations.dismissTap()
    }
    Column {
        if (item.type != AnnotationType.NOTE) {
            val current = item.color ?: AnnotationColor.defaultFor(item.type)
            ColorRow(current, Modifier.fillMaxWidth()) { change(item.type, it) }
            MarkIconRow(item.type, current, Modifier.fillMaxWidth()) { change(it, current) }
            if (AnnotationStroke.appliesTo(item.type)) {
                StrokeRow(item.stroke ?: AnnotationStroke.MEDIUM, current, Modifier.fillMaxWidth()) {
                    change(item.type, item.color, it)
                }
            }
        }
        Row {
            TextButton(
                onClick = {
                    annotations.startEdit(item.id)
                    annotations.dismissTap()
                },
            ) { Text(stringResource(R.string.reader_annotation_edit)) }
            TextButton(
                onClick = {
                    annotations.delete(item.id)
                    annotations.dismissTap()
                },
            ) { Text(stringResource(R.string.reader_annotation_delete)) }
            TextButton(onClick = { annotations.dismissTap() }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
