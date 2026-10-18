package com.paperly.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.library.toast

/**
 * Quick view: leaving a PDF that has marks which exist only here asks what to do, like other PDF apps do:
 * make a copy, replace the original, or discard. Returns the "leave" action for the back button and system back.
 */
@Composable
internal fun rememberQuickExit(viewModel: ReaderViewModel, doc: Document?, onLeave: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val hasMarks by viewModel.hasQuickMarks.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf(false) }
    val leaveClean: () -> Unit = {
        viewModel.leaveWithoutWriting()
        onLeave()
    }
    val request: () -> Unit = {
        if (viewModel.needsExitChoice()) {
            ask = true
        } else {
            onLeave()
        }
    }
    BackHandler(enabled = viewModel.isQuick && hasMarks, onBack = request)
    if (doc == null) return request
    val export = rememberDocumentExport(doc, viewModel::markedCopy, onSaved = leaveClean)
    if (ask) {
        QuickExitDialog(
            onReplace = {
                ask = false
                viewModel.replaceNow { message, ok ->
                    toast(context, message)
                    if (ok) leaveClean()
                }
            },
            onCopy = {
                ask = false
                export.saveCopy()
            },
            onDiscard = {
                ask = false
                leaveClean()
            },
            onDismiss = { ask = false },
        )
    }
    return request
}

@Composable
internal fun QuickExitDialog(onReplace: () -> Unit, onCopy: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_exit_title)) },
        text = { Text(stringResource(R.string.quick_exit_body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onCopy) { Text(stringResource(R.string.quick_exit_copy)) }
                TextButton(onClick = onReplace) { Text(stringResource(R.string.quick_exit_replace)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.quick_exit_discard)) }
            }
        },
    )
}
