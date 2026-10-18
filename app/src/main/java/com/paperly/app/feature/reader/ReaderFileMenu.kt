package com.paperly.app.feature.reader

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.DownloadGlyph
import com.paperly.app.core.ui.icons.PrintGlyph
import com.paperly.app.core.ui.icons.ShareGlyph
import com.paperly.app.feature.library.DocumentExport
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.library.toast

/** File actions of the open PDF; [saveToLibrary] exists only for a quick-view PDF that is not in the Library yet. */
internal class ReaderFileActions(
    val export: DocumentExport,
    val saveToLibrary: (() -> Unit)?,
    /** Quick view with marks: "Replace original with marks" (asks first); null = not offered. */
    val replaceOriginal: (() -> Unit)? = null,
    val withMarks: Boolean = false,
)

/** Must be called outside the dropdown: the Save-a-copy result launcher has to outlive the closing menu. */
@Composable
internal fun rememberReaderFileActions(viewModel: ReaderViewModel): ReaderFileActions? {
    QuickKeepPrompt(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val hasMarks by viewModel.hasQuickMarks.collectAsStateWithLifecycle()
    var confirmReplace by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(notice) {
        notice?.let {
            toast(context, it)
            viewModel.clearNotice()
        }
    }
    val doc = state.document ?: return null
    val withMarks = viewModel.isQuick && hasMarks
    val export = rememberDocumentExport(doc, if (withMarks) viewModel::markedCopy else null)
    ReplaceConfirm(confirmReplace, onYes = {
        confirmReplace = false
        viewModel.replaceOriginal()
    }, onNo = { confirmReplace = false })
    return ReaderFileActions(
        export = export,
        saveToLibrary = if (viewModel.isQuick && !saved) viewModel::saveToLibrary else null,
        replaceOriginal = if (withMarks) ({ confirmReplace = true }) else null,
        withMarks = withMarks,
    )
}

@Composable
internal fun ReplaceConfirm(show: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(stringResource(R.string.quick_replace_title)) },
        text = { Text(stringResource(R.string.quick_replace_body)) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.quick_replace_confirm)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun FileMenuItems(actions: ReaderFileActions, onDone: () -> Unit) {
    val saveLabel = if (actions.withMarks) R.string.quick_save_with_marks else R.string.doc_save_copy
    listOfNotNull<Triple<Int, ImageVector?, () -> Unit>>(
        actions.saveToLibrary?.let { Triple(R.string.quick_save_to_library, DownloadGlyph, it) },
        Triple(R.string.doc_share, ShareGlyph, actions.export.share),
        Triple(saveLabel, DownloadGlyph, actions.export.saveCopy),
        actions.replaceOriginal?.let { Triple(R.string.quick_replace_with_marks, null, it) },
        actions.export.print?.let { Triple(R.string.doc_print, PrintGlyph, it) },
    ).forEach { (label, icon, action) ->
        MenuRow(label, icon) {
            onDone()
            action()
        }
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
