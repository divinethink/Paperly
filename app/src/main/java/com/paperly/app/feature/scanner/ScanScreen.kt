package com.paperly.app.feature.scanner

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.paperly.app.R

/** Which picker the Scan screen opens by itself: nothing (Scan tab: camera/gallery buttons) or the gallery. */
enum class ScanMode { Camera, Gallery }

/**
 * P4: the Scan screen. The camera (the phone's own camera app) or the gallery gives the pictures; the unedited PDF
 * is saved to the Library as "scanned-pdf" and [onSaved] then opens the page editor in its crop step (crop ->
 * filter, no automatic detection). No picture = [onCancel].
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
    val cancelled = {
        viewModel.launchGate.arm()
        onCancel()
    }
    val camera = rememberCameraCapture(
        CameraResult(
            onPicture = { viewModel.onGalleryPicked(listOf(it.toString())) },
            onCancel = cancelled,
            onUnavailable = viewModel::onUnavailable,
        ),
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        if (it.isEmpty()) cancelled() else viewModel.onGalleryPicked(it.map { uri -> uri.toString() })
    }
    val openCamera = {
        viewModel.dismissError()
        camera()
    }
    val openGallery = {
        viewModel.dismissError()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) {
        viewModel.onScreenOpened() // a stuck "saving" from an earlier visit must not block
        // Scan tab: the buttons below let the user pick camera or gallery; the gallery menu entry opens the picker
        if (viewModel.launchGate.take(viewModel.state.value.saving) && mode == ScanMode.Gallery) openGallery()
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
    ScanFallbackContent(state, openCamera, openGallery)
}

/** What shows while the scanner starts or after it failed: a hint, the two buttons and the status. */
@Composable
private fun ScanFallbackContent(state: ScanUiState, onScan: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onScan, enabled = !state.saving) { Text(stringResource(R.string.scan_start)) }
        OutlinedButton(onClick = onGallery, enabled = !state.saving) {
            Text(stringResource(R.string.scan_gallery))
        }
        if (state.saving) Text(stringResource(R.string.scan_saving))
        state.error?.let {
            val message = if (it == ScanError.Unavailable) R.string.scan_error_unavailable else R.string.scan_error_save
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        }
    }
}
