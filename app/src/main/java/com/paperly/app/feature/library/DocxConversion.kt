package com.paperly.app.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.paperly.app.R
import com.paperly.app.data.convert.ConversionWorker
import com.paperly.app.domain.converter.ConversionError

internal const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val DOCX_CONVERT_TAG = "docx-convert"
private const val DOCX_EXTENSION = ".docx"

/** True when the picked file is a Word .docx (by MIME type, or by name when the provider reports a generic type). */
internal fun isDocx(context: Context, uri: Uri): Boolean {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    return mime == DOCX_MIME || displayName(context, uri)?.lowercase()?.endsWith(DOCX_EXTENSION) == true
}

/**
 * Queues a background DOCX -> EPUB conversion. A second request while one is pending is ignored (KEEP), so a
 * double tap cannot start two conversions. The original file is only read.
 */
internal fun enqueueDocxConversion(context: Context, uri: Uri) {
    // The worker may start after the picker's temporary grant is gone; ask for a persistable one (best-effort).
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val title = displayName(context, uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }.orEmpty()
    val request = OneTimeWorkRequestBuilder<ConversionWorker>()
        .setInputData(workDataOf(ConversionWorker.KEY_URI to uri.toString(), ConversionWorker.KEY_TITLE to title))
        .addTag(DOCX_CONVERT_TAG)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(DOCX_CONVERT_TAG, ExistingWorkPolicy.KEEP, request)
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

/** Banner for the current conversion: progress (cancellable), done (Open), already in Library, or a clear error. */
@Composable
internal fun DocxConversionStatus(onOpen: (documentId: String) -> Unit) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    val infos by manager.getWorkInfosByTagFlow(DOCX_CONVERT_TAG).collectAsStateWithLifecycle(emptyList())
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
                ConvertRunning { manager.cancelUniqueWork(DOCX_CONVERT_TAG) }
            WorkInfo.State.SUCCEEDED -> ConvertDone(info, onOpen, dismiss)
            WorkInfo.State.FAILED ->
                ConvertBanner(info.outputData.getString(ConversionWorker.KEY_ERROR).errorRes(), dismiss)
            else -> Unit
        }
    }
}

@Composable
private fun ConvertRunning(onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.convert_progress), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text(stringResource(R.string.export_cancel)) }
        }
    }
}

@Composable
private fun ConvertDone(info: WorkInfo, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val id = info.outputData.getString(ConversionWorker.KEY_DOCUMENT_ID)
    val duplicate = info.outputData.getString(ConversionWorker.KEY_DUPLICATE_TITLE)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            val message = if (duplicate != null) {
                stringResource(R.string.convert_duplicate, duplicate)
            } else {
                stringResource(R.string.convert_done)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (id != null) {
                    TextButton(onClick = {
                        onOpen(id)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.convert_open))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_dismiss)) }
            }
        }
    }
}

@Composable
private fun ConvertBanner(@StringRes message: Int, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_dismiss)) }
        }
    }
}

@StringRes
private fun String?.errorRes(): Int = when (this) {
    ConversionError.NOT_A_DOCX.name -> R.string.convert_failed_not_docx
    ConversionError.EMPTY.name -> R.string.convert_failed_empty
    ConversionError.TOO_LARGE.name -> R.string.convert_failed_large
    else -> R.string.convert_failed
}
