package com.paperly.app.feature.scanner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.paperly.app.core.file.ExportFiles
import java.io.File

/** What happens with the camera's result; kept together so the composable stays under the parameter limit. */
internal class CameraResult(
    val onPicture: (Uri) -> Unit,
    val onCancel: () -> Unit,
    val onUnavailable: () -> Unit,
)

/**
 * The phone's camera takes ONE picture into a temp file (no document-scanner screen, no automatic detection);
 * the app's own crop -> filter steps follow. Returns the function that starts the camera.
 */
@Composable
internal fun rememberCameraCapture(result: CameraResult): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = pending
        pending = null
        if (taken && path != null) result.onPicture(Uri.fromFile(File(path))) else result.onCancel()
    }
    return {
        val file = newCaptureFile(context)
        pending = file.path
        val started = runCatching {
            camera.launch(FileProvider.getUriForFile(context, ExportFiles.authority(context), file))
        }
        if (started.isFailure) {
            pending = null
            result.onUnavailable()
        }
    }
}

/** "Add scan" inside a document: the picture is added as a new page and opens in the crop step. */
@Composable
internal fun rememberScanCapture(viewModel: ScanEditViewModel): () -> Unit =
    rememberCameraCapture(
        CameraResult(
            onPicture = { viewModel.addPages(listOf(it.toString()), crop = true) },
            onCancel = viewModel::onAddCancelled,
            onUnavailable = viewModel::onAddUnavailable,
        ),
    )

/** One temp file; an older picture (already copied elsewhere) is removed first. */
private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "capture.jpg")
}

private const val CAPTURE_DIR = "scan_capture"
