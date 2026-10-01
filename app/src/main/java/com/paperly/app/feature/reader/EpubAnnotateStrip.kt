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
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType

// Strikethrough is omitted: Readium decorations have no strikethrough style.
private val STRIP_TYPES = listOf(
    AnnotationType.HIGHLIGHT to R.string.reader_annotation_highlight,
    AnnotationType.UNDERLINE to R.string.reader_annotation_underline,
    AnnotationType.NOTE to R.string.reader_annotation_note,
)

/** Top bar; when Annotate mode is on, the type/color strip sits right under it. */
@Composable
internal fun EpubTopOverlay(bar: EpubBarState, actions: EpubBarActions, annotations: EpubAnnotationController) {
    val annotating by annotations.mode.collectAsStateWithLifecycle()
    val style by annotations.style.collectAsStateWithLifecycle()
    Column {
        EpubTopBar(bar.copy(annotating = annotating), actions)
        if (annotating) EpubAnnotateStrip(style, annotations::setStyle)
    }
}

@Composable
private fun EpubAnnotateStrip(style: EpubAnnotateStyle, onStyle: (EpubAnnotateStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().background(barColor()).padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STRIP_TYPES.forEach { (type, label) ->
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
