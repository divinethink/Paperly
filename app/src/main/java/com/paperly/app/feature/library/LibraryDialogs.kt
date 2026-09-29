package com.paperly.app.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.MAX_TITLE_LENGTH
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.MAX_FOLDER_NAME_LENGTH
import com.paperly.app.domain.folder.normalizeFolderName

@Composable
internal fun RenameDialog(doc: Document, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(doc.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TITLE_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.rename_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.rename_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rename_cancel)) } },
    )
}

@Composable
internal fun DuplicateDialog(
    prompt: DuplicatePrompt,
    onSkip: () -> Unit,
    onKeepBoth: () -> Unit,
    onOpenExisting: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.duplicate_title)) },
        text = { Text(stringResource(R.string.duplicate_message, prompt.existingTitle)) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                TextButton(onClick = onOpenExisting) { Text(stringResource(R.string.duplicate_open_existing)) }
                TextButton(onClick = onKeepBoth) { Text(stringResource(R.string.duplicate_keep_both)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.duplicate_skip)) }
            }
        },
    )
}

internal sealed interface LibraryDialog {
    data class Rename(val doc: Document) : LibraryDialog
    data class Move(val doc: Document) : LibraryDialog
    data class Tags(val doc: Document) : LibraryDialog
    data object NewFolder : LibraryDialog
}

@Composable
internal fun LibraryDialogHost(
    dialog: LibraryDialog,
    folders: List<Folder>,
    library: LibraryViewModel,
    organize: OrganizeViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is LibraryDialog.Rename -> RenameDialog(
            doc = dialog.doc,
            onConfirm = {
                library.rename(dialog.doc, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.Move -> MoveFolderDialog(
            doc = dialog.doc,
            folders = folders,
            onPick = {
                organize.moveDocument(dialog.doc, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.Tags -> TagsDialog(
            doc = dialog.doc,
            onConfirm = {
                organize.setTags(dialog.doc, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        LibraryDialog.NewFolder -> NewFolderDialog(
            onConfirm = {
                organize.createFolder(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun MoveFolderDialog(doc: Document, folders: List<Folder>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (folders.isEmpty()) Text(stringResource(R.string.move_no_folders), Modifier.padding(bottom = 8.dp))
                val options = listOf<Pair<String?, String>>(null to stringResource(R.string.move_none)) +
                    folders.map { it.id to it.name }
                options.forEach { (id, name) ->
                    TextButton(onClick = { onPick(id) }, Modifier.fillMaxWidth()) {
                        Text(if (id == doc.folderId) "\u2713 $name" else name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TagsDialog(doc: Document, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(doc.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_TAGS_INPUT) },
                label = { Text(stringResource(R.string.tags_label)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NewFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_FOLDER_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = normalizeFolderName(text) != null) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TAGS_INPUT = 600
