package com.paperly.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.library.toast

/**
 * Quick-view EPUB: leaving a book with marks that exist only here asks what to do (make a copy, replace the
 * original, discard), like the PDF reader. Returns the \"leave\" action for the back button and system back.
 */
@Composable
internal fun rememberEpubQuickExit(viewModel: EpubReaderViewModel, onLeave: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val doc by viewModel.quickDocument.collectAsStateWithLifecycle()
    val hasMarks by viewModel.hasQuickMarks.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf(false) }
    val leaveClean: () -> Unit = { viewModel.leaveWithoutWriting(onLeave) }
    val request: () -> Unit = {
        if (viewModel.needsExitChoice()) {
            ask = true
        } else {
            onLeave()
        }
    }
    BackHandler(enabled = viewModel.isQuick && hasMarks, onBack = request)
    val document = doc ?: return request
    val export = rememberDocumentExport(document, viewModel::markedCopy, onSaved = leaveClean)
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
