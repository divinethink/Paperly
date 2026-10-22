package com.paperly.app.feature.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

/** What happens with the camera's result; kept together so the composable stays under the parameter limit. */
internal class CameraResult(
    val onPicture: (List<Uri>) -> Unit,
    val onCancel: () -> Unit,
    val onUnavailable: () -> Unit,
)

/**
 * The app's own camera (CameraX) takes one picture (or several in multi-page mode) into temp files; the crop ->
 * filter steps follow. Asks for the camera permission when needed (only here, never at startup). Returns the
 * function that starts the camera.
 */
@Composable
internal fun rememberCameraCapture(result: CameraResult): () -> Unit {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) open = true else result.onUnavailable()
    }
    if (open) {
        ScanCameraDialog(
            onPicture = {
                open = false
                result.onPicture(it)
            },
            onClose = {
                open = false
                result.onCancel()
            },
            onFailed = {
                open = false
                result.onUnavailable()
            },
        )
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> result.onUnavailable()
            granted -> open = true
            else -> permission.launch(Manifest.permission.CAMERA)
        }
    }
}

/** "Add scan" inside a document: the picture is added as a new page and opens in the crop step. */
@Composable
internal fun rememberScanCapture(viewModel: ScanEditViewModel): () -> Unit =
    rememberCameraCapture(
        CameraResult(
            onPicture = { viewModel.addPages(it.map(Uri::toString), crop = true) },
            onCancel = viewModel::onAddCancelled,
            onUnavailable = viewModel::onAddUnavailable,
        ),
    )

/** The first picture of a session ([index] 0) removes older ones (already copied elsewhere); later ones add files. */
internal fun newCaptureFile(context: Context, index: Int): File {
    val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
    if (index == 0) dir.listFiles()?.forEach { it.delete() }
    return File(dir, "capture_$index.jpg")
}

private const val CAPTURE_DIR = "scan_capture"
