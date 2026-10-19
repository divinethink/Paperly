package com.paperly.app.feature.library

import androidx.annotation.StringRes
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
import com.paperly.app.domain.folder.pathLabel

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
    /** [parentId] = the open folder on the Scanned tab (null = top level, and on the PDF/EPUB tabs). */
    data class NewFolder(val parentId: String?) : LibraryDialog
    data class RenameFolder(val folder: Folder) : LibraryDialog
    data class DeleteFolderWithDocs(val folder: Folder) : LibraryDialog
}

/** "Delete folder" acts at once (documents are kept); "+ documents" returns the confirm dialog to show. */
internal fun deleteFolderAction(
    state: LibraryUiState,
    withDocuments: Boolean,
    organize: OrganizeViewModel,
): LibraryDialog? {
    val folder = state.folders.firstOrNull { it.id == state.selectedFolderId } ?: return null
    if (withDocuments) return LibraryDialog.DeleteFolderWithDocs(folder)
    organize.deleteFolder(folder.id)
    return null
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
            currentFolderId = dialog.doc.folderId,
            folders = folders,
            onPick = {
                organize.moveDocument(dialog.doc, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.Tags -> TagsDialog(
            initial = dialog.doc.tags.joinToString(", "),
            onConfirm = {
                organize.setTags(dialog.doc, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.DeleteFolderWithDocs -> DeleteFolderWithDocsDialog(
            name = dialog.folder.name,
            onConfirm = {
                organize.deleteFolderWithDocuments(dialog.folder.id)
                library.selectFolder(dialog.folder.parentId) // an already-missing parent falls back to top level
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.NewFolder -> NewFolderDialog(
            onConfirm = {
                organize.createFolder(it, library.type.docType, dialog.parentId)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is LibraryDialog.RenameFolder -> RenameFolderDialog(dialog.folder, organize, onDismiss)
    }
}

@Composable
internal fun MoveFolderDialog(
    currentFolderId: String?,
    folders: List<Folder>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (folders.isEmpty()) Text(stringResource(R.string.move_no_folders), Modifier.padding(bottom = 8.dp))
                // Full path ("Bills › 2026"), sorted so a folder is followed by the ones inside it.
                val options = listOf<Pair<String?, String>>(null to stringResource(R.string.move_none)) +
                    folders.map { it.id to folders.pathLabel(it.id) }.sortedBy { it.second.lowercase() }
                options.forEach { (id, name) ->
                    TextButton(onClick = { onPick(id) }, Modifier.fillMaxWidth()) {
                        Text(if (id == currentFolderId) "\u2713 $name" else name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteFolderWithDocsDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_delete_docs_title)) },
        text = { Text(stringResource(R.string.folder_delete_docs_message, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_confirm_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun TagsDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    @StringRes title: Int = R.string.tags_title,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
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
private fun RenameFolderDialog(folder: Folder, organize: OrganizeViewModel, onDismiss: () -> Unit) {
    NewFolderDialog(
        onConfirm = {
            organize.renameFolder(folder.id, it)
            onDismiss()
        },
        onDismiss = onDismiss,
        initial = folder.name,
        title = R.string.folder_rename,
        confirm = R.string.rename_confirm,
    )
}

@Composable
private fun NewFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    @StringRes title: Int = R.string.folder_new,
    @StringRes confirm: Int = R.string.folder_create,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
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
                Text(stringResource(confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TAGS_INPUT = 600
