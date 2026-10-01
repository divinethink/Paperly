package com.paperly.app.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.data.export.ImageExportWorker
import java.io.File
import kotlinx.coroutines.launch

private const val IMAGE_EXPORT_TAG = "image-export"

/** Queues a background PDF -> JPG/PNG export; a second request while one is pending is ignored (KEEP). */
internal fun enqueueImageExport(context: Context, source: File, name: String, png: Boolean) {
    val format = if (png) ImageExportWorker.FORMAT_PNG else ImageExportWorker.FORMAT_JPG
    val request = OneTimeWorkRequestBuilder<ImageExportWorker>()
        .setInputData(
            workDataOf(
                ImageExportWorker.KEY_SOURCE to source.absolutePath,
                ImageExportWorker.KEY_NAME to name,
                ImageExportWorker.KEY_FORMAT to format,
            ),
        )
        .addTag(IMAGE_EXPORT_TAG)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(IMAGE_EXPORT_TAG, ExistingWorkPolicy.KEEP, request)
}

@Composable
internal fun ImageFormatDialog(onPick: (png: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_format_title)) },
        text = {
            Column {
                TextButton(onClick = { onPick(false) }) { Text(stringResource(R.string.export_format_jpg)) }
                TextButton(onClick = { onPick(true) }) { Text(stringResource(R.string.export_format_png)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) } },
    )
}

/** Banner for the current image export: progress (cancellable), ready (Share/Save), or failed. */
@Composable
internal fun ImageExportStatus() {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val infos by manager.getWorkInfosByTagFlow(IMAGE_EXPORT_TAG).collectAsStateWithLifecycle(emptyList())
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    val info = infos.firstOrNull { it.id.toString() != dismissed && it.state != WorkInfo.State.CANCELLED }
    val dismiss = {
        dismissed = info?.id?.toString()
        manager.pruneWork()
        Unit
    }
    if (info != null) {
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                ExportRunning(info) { manager.cancelUniqueWork(IMAGE_EXPORT_TAG) }
            WorkInfo.State.SUCCEEDED -> ExportReady(info, dismiss)
            WorkInfo.State.FAILED -> ExportBanner(R.string.export_images_failed, dismiss)
            else -> Unit
        }
    }
}

@Composable
private fun ExportRunning(info: WorkInfo, onCancel: () -> Unit) {
    val done = info.progress.getInt(ImageExportWorker.KEY_DONE, 0)
    val total = info.progress.getInt(ImageExportWorker.KEY_TOTAL, 0)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.export_images_progress, done, total),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (total > 0) {
                LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.export_cancel)) }
        }
    }
}

@Composable
private fun ExportReady(info: WorkInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = info.outputData.getString(ImageExportWorker.KEY_PATH)?.let(::File)?.takeIf { it.isFile }
    val mime = info.outputData.getString(ImageExportWorker.KEY_MIME).orEmpty()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) scope.launch { toast(context, saveTo(context, file, uri)) }
    }
    if (file == null) {
        ExportBanner(R.string.export_failed, onDismiss)
    } else {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.export_images_ready), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { shareReady(context, file, mime) }) {
                        Text(stringResource(R.string.doc_share))
                    }
                    TextButton(onClick = { saver.launch(file.name) }) {
                        Text(stringResource(R.string.doc_save_copy))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_dismiss)) }
                }
            }
        }
    }
}

@Composable
private fun ExportBanner(@StringRes message: Int, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_dismiss)) }
        }
    }
}

/** The file already lives in the FileProvider-exposed `exports/` folder, so it is shared in place. */
private fun shareReady(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, ExportFiles.authority(context), file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
