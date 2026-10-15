package com.paperly.app.feature.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.ScanOutputSize
import com.paperly.app.domain.scanner.cropReset
import com.paperly.app.domain.scanner.rotated
import com.paperly.app.domain.scanner.withFilter

/** S2 page editor: filter, 4-corner crop, rotate; Save rebuilds the PDF (original kept as a 7-day backup). */
@Composable
fun ScanEditScreen(onDone: () -> Unit, viewModel: ScanEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askSize by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
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
            EditControls(state, viewModel)
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
private fun EditControls(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val page = state.page ?: return
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (page.locked) Text(stringResource(R.string.scan_edit_locked), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanFilter.entries.forEach { filter ->
                FilterChip(
                    selected = page.filter == filter,
                    onClick = { viewModel.edit { it.withFilter(filter) } },
                    label = { Text(stringResource(filterLabel(filter))) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.goTo(state.current - 1) }, enabled = state.current > 0) {
                Text(stringResource(R.string.scan_edit_prev))
            }
            TextButton(onClick = {
                viewModel.edit(ScanEditPage::rotated)
            }, enabled = !page.locked) { Text(stringResource(R.string.scan_edit_rotate)) }
            TextButton(onClick = viewModel::toggleCrop, enabled = !page.locked) {
                Text(stringResource(if (state.cropping) R.string.scan_edit_crop_done else R.string.scan_edit_crop))
            }
            TextButton(onClick = {
                viewModel.edit(ScanEditPage::cropReset)
            }, enabled = !page.locked) { Text(stringResource(R.string.scan_edit_reset)) }
            val hasNext = state.current < state.pages.size - 1
            TextButton(onClick = { viewModel.goTo(state.current + 1) }, enabled = hasNext) {
                Text(stringResource(R.string.scan_edit_next))
            }
        }
    }
}

private fun filterLabel(filter: ScanFilter): Int = when (filter) {
    ScanFilter.ORIGINAL -> R.string.scan_filter_original
    ScanFilter.COLOR -> R.string.scan_filter_color
    ScanFilter.GRAY -> R.string.scan_filter_gray
    ScanFilter.BW -> R.string.scan_filter_bw
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

@Composable
private fun FailedDialog(result: ScanEditResult, onDismiss: () -> Unit) {
    val message = when (result) {
        ScanEditResult.OUT_OF_MEMORY -> R.string.scan_edit_error_memory
        else -> R.string.scan_edit_error_save
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
