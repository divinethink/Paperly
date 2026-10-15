package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.library.toast

/** Quick-view EPUB: keep-in-Library prompt after the first highlight + menu (Save to Library / Share / Save a copy). */
@Composable
internal fun EpubQuickMenu(viewModel: EpubReaderViewModel) {
    val prompt by viewModel.keepPrompt.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val doc by viewModel.quickDocument.collectAsStateWithLifecycle()
    val context = LocalContext.current
    QuickKeepDialog(prompt, viewModel::saveToLibrary, viewModel::dismissKeepPrompt)
    LaunchedEffect(notice) {
        notice?.let {
            toast(context, it)
            viewModel.clearNotice()
        }
    }
    val document = doc ?: return
    val export = rememberDocumentExport(document)
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FileMenuItems(ReaderFileActions(export, if (saved) null else viewModel::saveToLibrary)) { open = false }
        }
    }
}
