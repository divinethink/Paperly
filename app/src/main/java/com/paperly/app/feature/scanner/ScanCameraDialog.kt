package com.paperly.app.feature.scanner

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.paperly.app.R

private const val GRID_DIVISIONS = 3
private const val GRID_ALPHA = 0.5f
private val SHUTTER_SIZE = 76.dp
private val SHUTTER_RING = 4.dp
private val SHUTTER_GAP = 8.dp
private val SHUTTER_MARGIN = 24.dp

/**
 * The app's own full-screen camera (CameraX): pictures go to [onPicture] as temp-file Uris (one, or all taken in
 * multi-page mode) and the dialog is closed by the caller. [onClose] = Back / X with nothing taken, [onFailed] = the
 * picture could not be taken. Tap = focus, pinch = zoom.
 */
@Composable
internal fun ScanCameraDialog(onPicture: (List<Uri>) -> Unit, onClose: () -> Unit, onFailed: () -> Unit) {
    var shots by rememberSaveable { mutableStateOf(emptyList<String>()) } // multi-page pictures taken so far
    // Back / X: nothing taken -> cancel; pictures already taken are handed on (never thrown away)
    val finish = { if (shots.isEmpty()) onClose() else onPicture(shots.map(Uri::parse)) }
    Dialog(
        onDismissRequest = finish,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ScanCameraContent(shots, { shots = it }, onPicture, finish, onFailed)
    }
}

@Composable
private fun ScanCameraContent(
    shots: List<String>,
    onShots: (List<String>) -> Unit,
    onPicture: (List<Uri>) -> Unit,
    onFinish: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val cameraController = rememberScanCameraController()
    var flash by rememberSaveable { mutableStateOf(FlashChoice.Off) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var multi by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { applyFlash(cameraController, flash) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER // the whole picture, as it will be saved
                    controller = cameraController
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (grid) GridLines()
        CameraTopBar(
            flash = flash,
            grid = grid,
            onClose = onFinish,
            onFlash = { flash = FlashChoice.entries[(flash.ordinal + 1) % FlashChoice.entries.size] },
            onGrid = { grid = !grid },
        )
        ShutterButton(
            busy = busy,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            busy = true
            shoot(context, cameraController, shots.size, { uri ->
                val all = shots + uri.toString()
                if (multi) {
                    busy = false
                    onShots(all)
                } else {
                    onPicture(all.map(Uri::parse))
                }
            }) {
                busy = false
                onFailed()
            }
        }
        MultiControls(multi, shots.size, { multi = !multi }, onFinish, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun rememberScanCameraController(): LifecycleCameraController {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY // documents: quality before speed
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    return controller
}

@Composable
private fun CameraTopBar(
    flash: FlashChoice,
    grid: Boolean,
    onClose: () -> Unit,
    onFlash: () -> Unit,
    onGrid: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, stringResource(R.string.scan_camera_close), tint = Color.White)
        }
        Box(Modifier.weight(1f))
        CameraTextButton(flashLabel(flash), onFlash)
        CameraTextButton(if (grid) R.string.scan_camera_grid_on else R.string.scan_camera_grid_off, onGrid)
    }
}

/** Single / multi-page switch (left of the shutter) and, once pictures were taken, "Done (n)" (right). */
@Composable
private fun MultiControls(multi: Boolean, count: Int, onToggle: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = SHUTTER_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraTextButton(if (multi) R.string.scan_camera_multi else R.string.scan_camera_single, onToggle)
        Box(Modifier.weight(1f))
        if (count > 0) {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(stringResource(R.string.scan_camera_done, count))
            }
        }
    }
}

@Composable
private fun CameraTextButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun ShutterButton(busy: Boolean, modifier: Modifier, onShoot: () -> Unit) {
    val label = stringResource(R.string.scan_camera_shutter)
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = SHUTTER_MARGIN)
            .size(SHUTTER_SIZE)
            .clip(CircleShape)
            .border(SHUTTER_RING, Color.White, CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = !busy, role = Role.Button, onClick = onShoot)
            .padding(SHUTTER_GAP)
            .clip(CircleShape)
            .background(if (busy) Color.Gray else Color.White),
    )
}

/** Rule-of-thirds lines: help to hold the phone straight above the page. */
@Composable
private fun GridLines() {
    Canvas(Modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = GRID_ALPHA)
        val stroke = 1.dp.toPx()
        for (i in 1 until GRID_DIVISIONS) {
            val x = size.width * i / GRID_DIVISIONS
            val y = size.height * i / GRID_DIVISIONS
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Takes one picture into its own temp file (number [index]); any failure goes to [onFailed]. */
private fun shoot(
    context: Context,
    controller: LifecycleCameraController,
    index: Int,
    onSaved: (Uri) -> Unit,
    onFailed: () -> Unit,
) {
    val file = newCaptureFile(context, index)
    val callback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(Uri.fromFile(file))

        override fun onError(exception: ImageCaptureException) = onFailed()
    }
    val started = runCatching {
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            callback,
        )
    }
    if (started.isFailure) onFailed()
}
