package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationColor

private val AmberColor = Color(0xFFFFB300)
private val RedColor = Color(0xFFD32F2F)
private val GreenColor = Color(0xFF388E3C)
private val BlueColor = Color(0xFF1E88E5)
private val PurpleColor = Color(0xFF8E24AA)
private val BlackColor = Color(0xFF212121)
private const val SWATCHES_PER_ROW = 3

internal fun AnnotationColor.argb(): Color = when (this) {
    AnnotationColor.AMBER -> AmberColor
    AnnotationColor.RED -> RedColor
    AnnotationColor.GREEN -> GreenColor
    AnnotationColor.BLUE -> BlueColor
    AnnotationColor.PURPLE -> PurpleColor
    AnnotationColor.BLACK -> BlackColor
}

@Composable
internal fun ColorPicker(selected: AnnotationColor, onSelect: (AnnotationColor) -> Unit) {
    Text(
        stringResource(R.string.reader_annotation_color),
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
    )
    AnnotationColor.entries.chunked(SWATCHES_PER_ROW).forEach { rowItems ->
        Row(Modifier.fillMaxWidth()) {
            rowItems.forEach { option ->
                ColorSwatch(option, option == selected, Modifier.weight(1f)) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(option: AnnotationColor, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val name = stringResource(colorLabel(option))
    Box(
        modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(option.argb())
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.3f),
                    shape = CircleShape,
                ),
        )
    }
}

private fun colorLabel(color: AnnotationColor): Int = when (color) {
    AnnotationColor.AMBER -> R.string.reader_annotation_color_amber
    AnnotationColor.RED -> R.string.reader_annotation_color_red
    AnnotationColor.GREEN -> R.string.reader_annotation_color_green
    AnnotationColor.BLUE -> R.string.reader_annotation_color_blue
    AnnotationColor.PURPLE -> R.string.reader_annotation_color_purple
    AnnotationColor.BLACK -> R.string.reader_annotation_color_black
}
