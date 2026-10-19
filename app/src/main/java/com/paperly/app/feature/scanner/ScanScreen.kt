package com.paperly.app.feature.scanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

/** P4: ML Kit Document Scanner (its own camera + gallery-import UI) -> PDF -> Library as "scanned-pdf". */
@Composable
fun ScanScreen(onSaved: (editDocumentId: String?) -> Unit, viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val editorEnabled by viewModel.editorEnabled.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanned?.pages.orEmpty().map { it.imageUri.toString() }
            scanned?.pdf?.let { viewModel.onScanned(it.uri.toString(), pages) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.onGalleryPicked(uris.map { it.toString() })
    }
    LaunchedEffect(Unit) { viewModel.onScreenOpened() } // a stuck "saving" from an earlier visit must not block
    LaunchedEffect(state.saved) {
        if (state.saved) {
            val editId = state.editDocumentId
            if (state.duplicate) Toast.makeText(context, R.string.scan_duplicate, Toast.LENGTH_LONG).show()
            viewModel.consumeSaved()
            onSaved(editId)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                viewModel.dismissError()
                startScan(context.findActivity(), launcher, editorEnabled, viewModel::onUnavailable)
            },
            enabled = !state.saving,
        ) { Text(stringResource(R.string.scan_start)) }
        if (editorEnabled) {
            OutlinedButton(
                onClick = {
                    viewModel.dismissError()
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !state.saving,
            ) { Text(stringResource(R.string.scan_gallery)) }
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
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
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
