package com.paperly.app.feature.library

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.scanner.ExportSize

internal enum class ScanFormat(@StringRes val label: Int) {
    PDF(R.string.export_format_pdf),
    JPG(R.string.export_format_jpg),
}

/**
 * One dialog for Share / Send email / Save: editable file name (made safe by [ExportFiles.baseName]), PDF or JPG,
 * and the file size (Actual or smaller) with an estimated size for each option.
 */
@Composable
internal fun ScanFormatDialog(
    initialName: String,
    @StringRes confirmLabel: Int,
    estimate: suspend (pdf: Boolean) -> Map<ExportSize, Long>,
    onConfirm: (name: String, format: ScanFormat, size: ExportSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var format by remember { mutableStateOf(ScanFormat.PDF) }
    var size by remember { mutableStateOf(ExportSize.ACTUAL) }
    val estimates by produceState<Map<ExportSize, Long>>(emptyMap(), format) {
        value = emptyMap()
        value = runCatching { estimate(format == ScanFormat.PDF) }.getOrDefault(emptyMap())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_choose_format)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.export_file_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.export_format_label), Modifier.padding(top = 12.dp))
                Row {
                    ScanFormat.entries.forEach { option ->
                        ChoiceRow(stringResource(option.label), format == option) { format = option }
                    }
                }
                Text(stringResource(R.string.export_size_label), Modifier.padding(top = 12.dp))
                ExportSize.entries.forEach { option ->
                    val bytes = estimates[option]?.let { Formatter.formatShortFileSize(context, it) }
                    val label = stringResource(option.label)
                    ChoiceRow(
                        text = if (bytes == null) label else stringResource(R.string.export_size_option, label, bytes),
                        selected = size == option,
                    ) { size = option }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, format, size) }) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, Modifier.padding(start = 8.dp, end = 16.dp))
    }
}
