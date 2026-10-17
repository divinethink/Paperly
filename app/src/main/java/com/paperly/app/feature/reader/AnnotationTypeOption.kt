package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationType

@Composable
internal fun TypeOption(option: AnnotationType, selected: Boolean, onSelect: () -> Unit) {
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

internal fun typeLabel(type: AnnotationType): Int = when (type) {
    AnnotationType.HIGHLIGHT -> R.string.reader_annotation_highlight
    AnnotationType.UNDERLINE -> R.string.reader_annotation_underline
    AnnotationType.SQUIGGLY -> R.string.reader_annotation_squiggly
    AnnotationType.STRIKETHROUGH -> R.string.reader_annotation_strikethrough
    AnnotationType.NOTE -> R.string.reader_annotation_note
}
