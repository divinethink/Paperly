package com.paperly.app.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.paperly.app.R
import com.paperly.app.domain.document.MAX_TITLE_LENGTH
import com.paperly.app.domain.folder.Folder

/**
 * Asked after a scan returns. Not dismissible by tapping outside or Back: the only exits are Save and "Save as is",
 * both of which store the scan (a stray tap must not lose it).
 */
@Composable
internal fun ScanNameDialog(
    defaultTitle: String,
    folders: List<Folder>,
    onSave: (title: String, folderId: String?) -> Unit,
    onSaveAsIs: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.scan_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.scan_name_label)) },
                    singleLine = true,
                )
                if (folders.isNotEmpty()) {
                    Text(stringResource(R.string.scan_folder_label), style = MaterialTheme.typography.labelLarge)
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                        FolderChoice(stringResource(R.string.scan_folder_none), folderId == null) { folderId = null }
                        folders.forEach { FolderChoice(it.name, folderId == it.id) { folderId = it.id } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, folderId) }) { Text(stringResource(R.string.scan_save)) } },
        dismissButton = { TextButton(onClick = onSaveAsIs) { Text(stringResource(R.string.scan_save_as_is)) } },
    )
}

@Composable
private fun FolderChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
