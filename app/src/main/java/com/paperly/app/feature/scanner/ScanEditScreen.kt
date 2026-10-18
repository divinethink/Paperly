package com.paperly.app.feature.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanOutputSize

/** S2 page editor: filter, 4-corner crop, rotate; Save rebuilds the PDF (original kept as a 7-day backup). */
@Composable
fun ScanEditScreen(onDone: () -> Unit, viewModel: ScanEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askSize by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    BackHandler {
        when {
            state.saving -> Unit
            state.dirty -> askDiscard = true
            else -> onDone()
        }
    }
    if (askSize) {
        SizeDialog(
            onPick = {
                askSize = false
                viewModel.save(it)
            },
            onCancel = { askSize = false },
        )
    }
    if (askDiscard) DiscardDialog(onDiscard = onDone, onKeep = { askDiscard = false })
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        EditTopBar(state, onClose = { if (state.dirty) askDiscard = true else onDone() }, onSave = { askSize = true })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EditCanvas(state, viewModel)
            if (state.loading || state.saving) CircularProgressIndicator()
        }
        if (state.notEditable) {
            Text(stringResource(R.string.scan_edit_unavailable), Modifier.padding(16.dp))
        } else {
            EditControls(state, viewModel, onDelete = { askDelete = true })
        }
    }
}

@Composable
private fun EditTopBar(state: ScanEditUi, onClose: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose, enabled = !state.saving) { Text(stringResource(android.R.string.cancel)) }
        Text(
            stringResource(R.string.scan_edit_page_of, state.current + 1, state.pages.size.coerceAtLeast(1)),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onSave, enabled = state.dirty && !state.saving) {
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
private fun EditControls(state: ScanEditUi, viewModel: ScanEditViewModel, onDelete: () -> Unit) {
    val page = state.page ?: return
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (page.locked) Text(stringResource(R.string.scan_edit_locked), style = MaterialTheme.typography.bodySmall)
        FilterRow(state, page, viewModel)
        ToolRow(state, viewModel)
        PageManagementRow(state, viewModel, onDelete)
    }
}

private fun sizeLabel(size: ScanOutputSize): Int = when (size) {
    ScanOutputSize.SMALL -> R.string.scan_size_small
    ScanOutputSize.MEDIUM -> R.string.scan_size_medium
    ScanOutputSize.LARGE -> R.string.scan_size_large
}

@Composable
private fun SizeDialog(onPick: (ScanOutputSize) -> Unit, onCancel: () -> Unit) {
    var chosen by remember { mutableStateOf(ScanOutputSize.MEDIUM) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.scan_size_title)) },
        text = {
            Column {
                Text(stringResource(R.string.scan_size_hint), style = MaterialTheme.typography.bodySmall)
                ScanOutputSize.entries.forEach { size ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(chosen == size, role = Role.RadioButton, onClick = { chosen = size }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == size, onClick = null)
                        Text(stringResource(sizeLabel(size)), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text(stringResource(R.string.scan_save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
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
