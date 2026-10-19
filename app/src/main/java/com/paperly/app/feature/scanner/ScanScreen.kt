package com.paperly.app.feature.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.paperly.app.R

/** Which picker the Scan screen opens by itself: the document scanner (Scan tab) or the gallery (Scanned ⋮ menu). */
enum class ScanMode { Camera, Gallery }

/**
 * P4: opens ML Kit Document Scanner (or the gallery picker) at once; the result is saved to the Library as
 * "scanned-pdf" and then [onSaved] says where to go (editor, Reader or Library). No result = [onCancel].
 * The buttons stay as a fallback when the scanner cannot start.
 */
@Composable
fun ScanScreen(
    mode: ScanMode,
    onSaved: (editDocumentId: String?, openDocumentId: String?) -> Unit,
    onCancel: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val editorEnabled by viewModel.editorEnabled.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        onScanResult(it, viewModel, onCancel)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        onGalleryResult(it, viewModel, onCancel)
    }
    val openScanner = {
        viewModel.dismissError()
        startScan(context.findActivity(), launcher, viewModel.editorEnabled.value, viewModel::onUnavailable)
    }
    val openGallery = {
        viewModel.dismissError()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) {
        viewModel.onScreenOpened() // a stuck "saving" from an earlier visit must not block
        if (viewModel.launchGate.take(viewModel.state.value.saving)) {
            when {
                mode == ScanMode.Camera -> {
                    // editor ON: the buttons below let the user pick camera or gallery (gallery opens the crop)
                    if (!viewModel.editorEnabled.value) openScanner()
                }
                viewModel.editorEnabled.value -> openGallery()
                else -> onCancel() // the gallery path needs the page editor
            }
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            val editId = state.editDocumentId
            val openId = state.openDocumentId
            if (state.duplicate) Toast.makeText(context, R.string.scan_duplicate, Toast.LENGTH_LONG).show()
            viewModel.consumeSaved()
            onSaved(editId, openId)
        }
    }
    ScanFallbackContent(state, editorEnabled, openScanner, openGallery)
}

/** The scanner closed: a result is saved, no result (back / failure) goes [onCancel] and re-arms the next start. */
private fun onScanResult(result: ActivityResult, viewModel: ScanViewModel, onCancel: () -> Unit) {
    val scanned = if (result.resultCode == Activity.RESULT_OK) {
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)
    } else {
        null
    }
    val pdf = scanned?.pdf
    if (scanned == null || pdf == null) {
        viewModel.launchGate.arm()
        onCancel()
    } else {
        viewModel.onScanned(pdf.uri.toString(), scanned.pages.orEmpty().map { it.imageUri.toString() })
    }
}

private fun onGalleryResult(uris: List<Uri>, viewModel: ScanViewModel, onCancel: () -> Unit) {
    if (uris.isEmpty()) {
        viewModel.launchGate.arm()
        onCancel()
    } else {
        viewModel.onGalleryPicked(uris.map { it.toString() })
    }
}

/** What shows while the scanner starts or after it failed: a hint, the two buttons and the status. */
@Composable
private fun ScanFallbackContent(
    state: ScanUiState,
    editorEnabled: Boolean,
    onScan: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onScan, enabled = !state.saving) { Text(stringResource(R.string.scan_start)) }
        if (editorEnabled) {
            OutlinedButton(onClick = onGallery, enabled = !state.saving) {
                Text(stringResource(R.string.scan_gallery))
            }
        }
        if (state.saving) Text(stringResource(R.string.scan_saving))
        state.error?.let {
            val message = if (it == ScanError.Unavailable) R.string.scan_error_unavailable else R.string.scan_error_save
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        }
    }
}

internal fun startScan(
    activity: Activity?,
    launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    keepPages: Boolean,
    onUnavailable: () -> Unit,
) {
    if (activity == null) {
        onUnavailable()
        return
    }
    val builder = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
    // S2: the page JPEGs are kept (with the PDF) so pages can be edited after saving; flag OFF = PDF only, as before
    val options = if (keepPages) {
        builder.setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
        )
    } else {
        builder.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
    }.build()
    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
        .addOnFailureListener { onUnavailable() }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
