package com.paperly.app.feature.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.cropReset

/** S2 page editor: filter, 4-corner crop, rotate; Save rebuilds the PDF (original kept as a 7-day backup). */
@Composable
fun ScanEditScreen(
    onDone: (openDocumentId: String?) -> Unit,
    onCancelAdd: () -> Unit,
    onDiscarded: (retake: Boolean) -> Unit,
    viewModel: ScanEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askDiscard by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val leave = { onDone(viewModel.exitDocumentId) }
    EditExitEffects(state, leave, onCancelAdd) { onDiscarded(viewModel.fromCamera) }
    // Back on a fresh scan always asks first (same answer every time); an older document asks only after edits.
    val close: () -> Unit = {
        when {
            state.saving -> Unit
            viewModel.isNewScan || state.dirty -> askDiscard = true
            else -> leave()
        }
    }
    BackHandler(onBack = close)
    if (askDiscard) {
        DiscardDialog(
            newScan = viewModel.isNewScan,
            onDiscard = if (viewModel.isNewScan) viewModel::discardNewScan else leave,
            onKeep = { askDiscard = false },
        )
    }
    if (askDelete) {
        DeleteDialog(
            onDelete = {
                askDelete = false
                viewModel.deleteCurrent()
            },
            onCancel = { askDelete = false },
        )
    }
    ErrorDialogs(state, viewModel)
    var adjusting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditTopBar(state, onClose = close, onSave = viewModel::save)
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

/** Crop step = "Border adjustment"; the filter / page step has its own title. */
private fun editTitle(cropping: Boolean): Int =
    if (cropping) R.string.scan_edit_border_title else R.string.scan_edit_page_title

@Composable
private fun EditTopBar(state: ScanEditUi, onClose: () -> Unit, onSave: () -> Unit) {
    PaperlyTopBar(stringResource(editTitle(state.cropping)), onBack = { if (!state.saving) onClose() }) {
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
    if (state.awaitingAdd) return // picker/scanner is (about to be) up: not the old page behind it
    when {
        page != null && state.cropping && source != null ->
            ScanCropOverlay(source, page.quad, viewModel::setQuad, page.rotation, Modifier.fillMaxSize())
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
        if (state.cropping) {
            CropStepRow(viewModel)
        } else {
            FilterRow(state, page, viewModel)
            if (adjusting) AdjustPanel(page, viewModel)
            ToolRow(state, viewModel)
            if (state.pages.size > 1) PageStrip(state, viewModel)
            PageManagementRow(state, viewModel, onDelete)
        }
    }
}

/** Step 1 (crop): only "Full image" and "Next" - the filters and B/W come on the next step, on the cropped page. */
@Composable
private fun CropStepRow(viewModel: ScanEditViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { viewModel.edit(ScanEditPage::cropReset) }, colors = actionTextColors()) {
            Text(stringResource(R.string.scan_edit_full_image))
        }
        Button(onClick = viewModel::toggleCrop) { Text(stringResource(R.string.scan_edit_crop_next)) }
    }
}

@Composable
private fun DiscardDialog(newScan: Boolean, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val title = if (newScan) R.string.scan_edit_discard_scan_title else R.string.scan_edit_discard_title
    val body = if (newScan) R.string.scan_edit_discard_scan_body else R.string.scan_edit_discard_body
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
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

/** The Reader asked to delete a page that carries highlights or is the last page. */
@Composable
private fun BlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.scan_edit_delete_blocked)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** Error / info dialogs of the editor (kept out of [ScanEditScreen] to stay under the complexity limit). */
@Composable
private fun ErrorDialogs(state: ScanEditUi, viewModel: ScanEditViewModel) {
    if (state.addFailed) FailedDialog(null, viewModel::dismissError)
    state.failed?.let { FailedDialog(it, viewModel::dismissError) }
    if (state.deleteBlocked) BlockedDialog(viewModel::dismissDeleteBlocked)
}
