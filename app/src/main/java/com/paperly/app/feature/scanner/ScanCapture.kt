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

/**
 * "Add scan" from a document: the phone's camera takes one picture, which then goes through the app's own
 * crop -> filter steps (same as gallery pictures). Returns the function that starts the camera.
 */
@Composable
internal fun rememberScanCapture(viewModel: ScanEditViewModel): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = pending
        pending = null
        if (taken && path != null) {
            viewModel.addPages(listOf(Uri.fromFile(File(path)).toString()), crop = true)
        } else {
            viewModel.onAddCancelled()
        }
    }
    return {
        val file = newCaptureFile(context)
        pending = file.path
        val started = runCatching {
            camera.launch(FileProvider.getUriForFile(context, ExportFiles.authority(context), file))
        }
        if (started.isFailure) {
            pending = null
            viewModel.onAddUnavailable()
        }
    }
}

/** One temp file; an older picture (already copied into the editor) is removed first. */
private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "capture.jpg")
}

private const val CAPTURE_DIR = "scan_capture"
