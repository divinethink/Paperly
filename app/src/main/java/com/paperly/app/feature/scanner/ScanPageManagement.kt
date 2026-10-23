package com.paperly.app.feature.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.paperly.app.R
import kotlinx.coroutines.launch

/**
 * S4: adding pages when the editor was opened from "Add scan" / "Add pictures" (no buttons here any more: the
 * Pages screen has them). Reordering = hold & drag in the page strip; deleting = Pages screen (select).
 */
@Composable
internal fun PageAddEffects(state: ScanEditUi, viewModel: ScanEditViewModel) {
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
}

/** OCR shortcut: copies the recognised text of the saved scan (read-only; unsaved edits are not affected). */
@Composable
internal fun rememberCopyText(viewModel: ScanEditViewModel): () -> Unit {
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
