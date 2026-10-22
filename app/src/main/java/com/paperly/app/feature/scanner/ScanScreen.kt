package com.paperly.app.feature.scanner

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.CameraGlyph
import com.paperly.app.core.ui.icons.GalleryGlyph

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
                viewModel.onGalleryPicked(it.map(Uri::toString))
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
    if (state.saving && fromCamera) CameraSavingContent() else ScanFallbackContent(state, openCamera, openGallery)
}

/** Right after the shutter: black like the camera, so the wait does not flash the Scan buttons screen. */
@Composable
private fun CameraSavingContent() {
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(stringResource(R.string.scan_saving), Modifier.padding(top = 16.dp), color = Color.White)
    }
}

/** What shows while the scanner starts or after it failed: a hint, the two choice cards and the status. */
@Composable
private fun ScanFallbackContent(state: ScanUiState, onScan: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
        // Gallery on the left, Camera on the right (the primary action sits under the thumb).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val gallery = ScanChoice(GalleryGlyph, R.string.scan_card_gallery, primary = false, onClick = onGallery)
            val camera = ScanChoice(CameraGlyph, R.string.scan_card_camera, primary = true, onClick = onScan)
            ScanChoiceCard(gallery, !state.saving, Modifier.weight(1f))
            ScanChoiceCard(camera, !state.saving, Modifier.weight(1f))
        }
        if (state.saving) Text(stringResource(R.string.scan_saving))
        state.error?.let {
            val message = if (it == ScanError.Unavailable) R.string.scan_error_unavailable else R.string.scan_error_save
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
        }
    }
}

private class ScanChoice(
    val icon: ImageVector,
    @StringRes val label: Int,
    val primary: Boolean,
    val onClick: () -> Unit,
)

/** One big tappable card with an icon over its name (Material 3 filled surface, 120dp tall). */
@Composable
private fun ScanChoiceCard(choice: ScanChoice, enabled: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = choice.onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (choice.primary) scheme.primary else scheme.secondaryContainer,
        contentColor = if (choice.primary) scheme.onPrimary else scheme.onSecondaryContainer,
        modifier = modifier.height(ScanCardHeight),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(choice.icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(
                stringResource(choice.label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val ScanCardHeight = 120.dp

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
