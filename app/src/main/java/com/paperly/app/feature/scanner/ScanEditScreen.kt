package com.paperly.app.feature.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.core.ui.topBarTextButtonColors
import com.paperly.app.domain.scanner.ScanEditResult

/** S2 page editor: filter, 4-corner crop, rotate; Save rebuilds the PDF (original kept as a 7-day backup). */
@Composable
fun ScanEditScreen(onDone: (openDocumentId: String?) -> Unit, viewModel: ScanEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askDiscard by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val leave = { onDone(viewModel.exitDocumentId) }
    LaunchedEffect(state.done) { if (state.done) leave() }
    BackHandler {
        when {
            state.saving -> Unit
            state.dirty -> askDiscard = true
            else -> leave()
        }
    }
    if (askDiscard) DiscardDialog(onDiscard = leave, onKeep = { askDiscard = false })
    if (askDelete) {
        DeleteDialog(
            onDelete = {
                askDelete = false
                viewModel.deleteCurrent()
            },
            onCancel = { askDelete = false },
        )
    }
    if (state.addFailed) FailedDialog(null, viewModel::dismissError)
    state.failed?.let { FailedDialog(it, viewModel::dismissError) }
    var adjusting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditTopBar(state, onClose = { if (state.dirty) askDiscard = true else leave() }, onSave = viewModel::save)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EditCanvas(state, viewModel)
            if (state.loading || state.saving) ProcessingCard()
        }
        if (state.notEditable) {
            Text(stringResource(R.string.scan_edit_unavailable), Modifier.padding(16.dp).navigationBarsPadding())
        } else {
            EditControls(state, viewModel, adjusting, onDelete = { askDelete = true })
            EditBottomBar(state, viewModel) { adjusting = !adjusting }
        }
    }
}

@Composable
private fun EditTopBar(state: ScanEditUi, onClose: () -> Unit, onSave: () -> Unit) {
    PaperlyTopBar(stringResource(R.string.scan_edit_border_title), onBack = { if (!state.saving) onClose() }) {
        TextButton(onClick = onSave, enabled = state.dirty && !state.saving, colors = topBarTextButtonColors()) {
            Text(stringResource(R.string.scan_save))
        }
    }
}

@Composable
private fun EditCanvas(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val page = state.page
    val source = state.source
    val preview = state.preview
    when {
        page != null && state.cropping && source != null ->
            ScanCropOverlay(source, page.quad, viewModel::setQuad, Modifier.fillMaxSize())
        preview != null ->
            Image(
                preview,
                stringResource(R.string.scan_edit_preview),
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
    }
}

@Composable
private fun EditControls(state: ScanEditUi, viewModel: ScanEditViewModel, adjusting: Boolean, onDelete: () -> Unit) {
    val page = state.page ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (page.locked) Text(stringResource(R.string.scan_edit_locked), style = MaterialTheme.typography.bodySmall)
        FilterRow(state, page, viewModel)
        if (adjusting) AdjustPanel(page, viewModel)
        ToolRow(state, viewModel)
        if (state.pages.size > 1) PageStrip(state, viewModel)
        PageManagementRow(state, viewModel, onDelete)
    }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.scan_edit_discard_title)) },
        text = { Text(stringResource(R.string.scan_edit_discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.scan_edit_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(R.string.scan_edit_keep)) } },
    )
}

/** [result] null = adding pictures failed (nothing was changed). */
@Composable
private fun FailedDialog(result: ScanEditResult?, onDismiss: () -> Unit) {
    val message = when (result) {
        null -> R.string.scan_edit_error_add
        ScanEditResult.OUT_OF_MEMORY -> R.string.scan_edit_error_memory
        else -> R.string.scan_edit_error_save
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
