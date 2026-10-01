package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType

// EPUB omits strikethrough: Readium decorations have no strikethrough style.
private val EPUB_STRIP_TYPES = listOf(
    AnnotationType.HIGHLIGHT to R.string.reader_annotation_highlight,
    AnnotationType.UNDERLINE to R.string.reader_annotation_underline,
    AnnotationType.NOTE to R.string.reader_annotation_note,
)
private val PDF_STRIP_TYPES = listOf(
    AnnotationType.HIGHLIGHT to R.string.reader_annotation_highlight,
    AnnotationType.UNDERLINE to R.string.reader_annotation_underline,
    AnnotationType.STRIKETHROUGH to R.string.reader_annotation_strikethrough,
    AnnotationType.NOTE to R.string.reader_annotation_note,
)

/** Top bar; when Annotate mode is on, the type/color strip sits right under it. */
@Composable
internal fun EpubTopOverlay(bar: EpubBarState, actions: EpubBarActions, annotations: EpubAnnotationController) {
    val annotating by annotations.mode.collectAsStateWithLifecycle()
    val style by annotations.style.collectAsStateWithLifecycle()
    Column {
        EpubTopBar(bar.copy(annotating = annotating), actions)
        if (annotating) AnnotateStrip(style, EPUB_STRIP_TYPES, annotations::setStyle)
    }
}

/** PDF Annotate strip (same look as EPUB); shown only while the pencil is on. */
@Composable
internal fun PdfAnnotateStrip(style: AnnotateStyle, onStyle: (AnnotateStyle) -> Unit) =
    AnnotateStrip(style, PDF_STRIP_TYPES, onStyle)

@Composable
private fun AnnotateStrip(
    style: AnnotateStyle,
    types: List<Pair<AnnotationType, Int>>,
    onStyle: (AnnotateStyle) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(barColor()).padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { (type, label) ->
                FilterChip(
                    selected = style.type == type,
                    onClick = { onStyle(style.copy(type = type)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            AnnotationColor.entries.forEach { color ->
                ColorSwatch(color, color == style.color, Modifier.weight(1f)) { onStyle(style.copy(color = color)) }
            }
        }
    }
}
