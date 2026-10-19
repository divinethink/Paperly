package com.paperly.app.feature.scanner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.paperly.app.R
import kotlinx.coroutines.launch

/** S4: move / add / delete pages. Nothing changes in the file until Save. */
@Composable
internal fun PageManagementRow(state: ScanEditUi, viewModel: ScanEditViewModel, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            viewModel.addPages(scanned?.pages.orEmpty().map { it.imageUri.toString() })
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addPages(uris.map { it.toString() })
    }
    val busy = state.saving || state.loading
    val copyText = rememberCopyText(viewModel)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(R.string.scan_edit_move_earlier, !busy && state.current > 0) { viewModel.move(-1) }
            ActionButton(R.string.scan_edit_move_later, !busy && state.current < state.pages.size - 1) {
                viewModel.move(1)
            }
            ActionButton(R.string.scan_edit_add_scan, !busy) {
                val unavailable = viewModel::onAddUnavailable
                startScan(context.findActivity(), scanner, keepPages = true, onUnavailable = unavailable)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(R.string.scan_edit_add_gallery, !busy) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ActionButton(R.string.scan_text_copy, !busy, onClick = copyText)
            ActionButton(R.string.scan_edit_delete, !busy && state.canDelete, onClick = onDelete)
        }
    }
}

/** Equal-width text button (3 per row): the label never runs off the screen edge. */
@Composable
private fun RowScope.ActionButton(label: Int, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = ButtonPadding) {
        Text(
            stringResource(label),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private val ButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)

/** OCR shortcut: copies the recognised text of the saved scan (read-only; unsaved edits are not affected). */
@Composable
private fun rememberCopyText(viewModel: ScanEditViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val text = viewModel.recognisedText()
            if (text == null) {
                Toast.makeText(context, R.string.scan_text_none, Toast.LENGTH_LONG).show()
            } else {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("scan", text))
                Toast.makeText(context, R.string.scan_text_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
internal fun DeleteDialog(onDelete: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.scan_edit_delete_title)) },
        text = { Text(stringResource(R.string.scan_edit_delete_body)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.scan_edit_delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}
