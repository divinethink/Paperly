package com.paperly.app.feature.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.feature.library.DocumentExport
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.library.toast

/** File actions of the open PDF; [saveToLibrary] exists only for a quick-view PDF that is not in the Library yet. */
internal class ReaderFileActions(val export: DocumentExport, val saveToLibrary: (() -> Unit)?)

/** Must be called outside the dropdown: the Save-a-copy result launcher has to outlive the closing menu. */
@Composable
internal fun rememberReaderFileActions(viewModel: ReaderViewModel): ReaderFileActions? {
    QuickKeepPrompt(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice?.let {
            toast(context, it)
            viewModel.clearNotice()
        }
    }
    val doc = state.document ?: return null
    val export = rememberDocumentExport(doc)
    return ReaderFileActions(export, if (viewModel.isQuick && !saved) viewModel::saveToLibrary else null)
}

@Composable
internal fun FileMenuItems(actions: ReaderFileActions, onDone: () -> Unit) {
    listOfNotNull<Pair<Int, () -> Unit>>(
        actions.saveToLibrary?.let { R.string.quick_save_to_library to it },
        R.string.doc_share to actions.export.share,
        R.string.doc_save_copy to actions.export.saveCopy,
        actions.export.print?.let { R.string.doc_print to it },
    ).forEach { (label, action) ->
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                onDone()
                action()
            },
        )
    }
}

/** Quick view, after the first highlight: highlights live only with a Library copy, so offer to keep the file. */
@Composable
private fun QuickKeepPrompt(viewModel: ReaderViewModel) {
    val show by viewModel.keepPrompt.collectAsStateWithLifecycle()
    QuickKeepDialog(show, viewModel::saveToLibrary, viewModel::dismissKeepPrompt)
}

@Composable
internal fun QuickKeepDialog(show: Boolean, onSave: () -> Unit, onLater: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.quick_keep_title)) },
        text = { Text(stringResource(R.string.quick_keep_body)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.quick_save_to_library)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.quick_keep_later)) } },
    )
}
