package com.paperly.app.feature.scanner

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.CameraGlyph
import com.paperly.app.core.ui.icons.CopyGlyph
import com.paperly.app.core.ui.icons.GalleryGlyph
import kotlinx.coroutines.launch

/** S4: move / add / delete pages. Nothing changes in the file until Save. */
@Composable
internal fun PageManagementRow(state: ScanEditUi, viewModel: ScanEditViewModel, onDelete: () -> Unit) {
    val capture = rememberScanCapture(viewModel)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) viewModel.onAddCancelled() else viewModel.addPages(uris.map { it.toString() }, crop = true)
    }
    val pickPictures = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val busy = state.saving || state.loading
    LaunchedEffect(state.autoAdd, state.autoPick, busy) {
        if (!busy && state.autoAdd) {
            viewModel.consumeAutoAdd()
            capture()
        } else if (!busy && state.autoPick) {
            viewModel.consumeAutoPick()
            pickPictures()
        }
    }
    val copyText = rememberCopyText(viewModel)
    Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            ActionTile(
                Icons.AutoMirrored.Filled.ArrowBack,
                R.string.scan_edit_move_earlier,
                !busy && state.current > 0,
            ) { viewModel.move(-1) }
            ActionTile(
                Icons.AutoMirrored.Filled.ArrowForward,
                R.string.scan_edit_move_later,
                !busy && state.current < state.pages.size - 1,
            ) { viewModel.move(1) }
            ActionTile(CopyGlyph, R.string.scan_text_copy, !busy, onClick = copyText)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            ActionTile(GalleryGlyph, R.string.scan_edit_add_gallery, !busy, onClick = pickPictures)
            ActionTile(CameraGlyph, R.string.scan_edit_add_scan, !busy, onClick = capture)
            ActionTile(
                Icons.Filled.Delete,
                R.string.scan_edit_delete,
                !busy && state.canDelete,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

private val TileGap = 8.dp
private val TileMinHeight = 64.dp
private val TileCorner = 12.dp

/** Outlined tile (3 per row): icon over a short label, so the label never runs off the screen edge. */
@Composable
private fun RowScope.ActionTile(
    icon: ImageVector,
    label: Int,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = TileMinHeight),
        shape = RoundedCornerShape(TileCorner),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(
                stringResource(label),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

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
