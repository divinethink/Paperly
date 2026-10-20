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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** [requested] = a scan was just thrown away and the camera should open again; [handled] clears that request. */
class ScanRetake(val requested: Boolean, val handled: () -> Unit)

/**
 * P4: the Scan screen. The in-app camera or the gallery gives the pictures; the unedited PDF
 * is saved to the Library as "scanned-pdf" and [onSaved] then opens the page editor in its crop step (crop ->
 * filter, no automatic detection). No picture = [onCancel].
 */
@Composable
fun ScanScreen(
    mode: ScanMode,
    retake: ScanRetake,
    onSaved: (editDocumentId: String?, openDocumentId: String?, fromCamera: Boolean) -> Unit,
    onCancel: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancelled = {
        viewModel.launchGate.arm()
        onCancel()
    }
    var fromCamera by rememberSaveable { mutableStateOf(false) } // the last picture came from the camera
    val camera = rememberCameraCapture(
        CameraResult(
            onPicture = {
                fromCamera = true
                viewModel.onGalleryPicked(listOf(it.toString()))
            },
            onCancel = cancelled,
            onUnavailable = viewModel::onUnavailable,
        ),
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        if (it.isEmpty()) {
            cancelled()
        } else {
            fromCamera = false
            viewModel.onGalleryPicked(it.map { uri -> uri.toString() })
        }
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
        val first = viewModel.launchGate.take(viewModel.state.value.saving)
        if (first && mode == ScanMode.Gallery && !retake.requested) openGallery()
    }
    LaunchedEffect(retake.requested) {
        if (retake.requested) {
            retake.handled()
            openCamera() // a discarded scan: straight back to the camera
        }
    }
    SavedEffect(state, viewModel) { editId, openId -> onSaved(editId, openId, fromCamera) }
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

/** The save finished: tells the user about a duplicate, resets the state and moves on (editor / Library). */
@Composable
private fun SavedEffect(state: ScanUiState, viewModel: ScanViewModel, onSaved: (String?, String?) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(state.saved) {
        if (state.saved) {
            val editId = state.editDocumentId
            val openId = state.openDocumentId
            if (state.duplicate) Toast.makeText(context, R.string.scan_duplicate, Toast.LENGTH_LONG).show()
            viewModel.consumeSaved()
            onSaved(editId, openId)
        }
    }
}
