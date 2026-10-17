package com.paperly.app.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.document.Document
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-document export actions; [print] is null for non-PDF documents. */
internal class DocumentExport(
    val share: () -> Unit,
    val saveCopy: () -> Unit,
    val print: (() -> Unit)?,
    val exportImages: (() -> Unit)?,
)

@Composable
internal fun rememberDocumentExport(doc: Document): DocumentExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ExportViewModel = hiltViewModel()
    val name = ExportFiles.fileName(doc.title, doc.type)
    val mime = ExportFiles.mimeType(doc.type)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) scope.launch { toast(context, saveTo(context, viewModel.file(doc.id), uri)) }
    }
    var askFormat by remember { mutableStateOf(false) }
    if (askFormat) {
        ImageFormatDialog(
            onPick = { png ->
                askFormat = false
                viewModel.file(doc.id)?.let { enqueueImageExport(context, it, ExportFiles.baseName(doc.title), png) }
            },
            onDismiss = { askFormat = false },
        )
    }
    val isPdf = ExportFiles.isPdf(doc.type)
    val isScan = doc.type == SCAN_TYPE
    // Scans: Share / Save a copy first ask PDF or JPG (JPG = the existing background image export).
    var askScan by remember { mutableStateOf<ScanAction?>(null) }
    askScan?.let { action ->
        ScanFormatDialog(
            onPdf = {
                askScan = null
                if (action == ScanAction.SHARE) {
                    scope.launch { shareFile(context, viewModel.file(doc.id), name, mime) }
                } else {
                    saver.launch(name)
                }
            },
            onJpg = {
                askScan = null
                viewModel.file(doc.id)?.let { enqueueImageExport(context, it, ExportFiles.baseName(doc.title), false) }
                toast(context, R.string.export_jpg_started)
            },
            onDismiss = { askScan = null },
        )
    }
    return DocumentExport(
        share = {
            if (isScan) {
                askScan = ScanAction.SHARE
            } else {
                scope.launch { shareFile(context, viewModel.file(doc.id), name, mime) }
            }
        },
        saveCopy = { if (isScan) askScan = ScanAction.SAVE else saver.launch(name) },
        print = if (isPdf) {
            { printFile(context, viewModel.file(doc.id), name) }
        } else {
            null
        },
        exportImages = if (isPdf) {
            { askFormat = true }
        } else {
            null
        },
    )
}

private const val SCAN_TYPE = "scanned-pdf"

private enum class ScanAction { SHARE, SAVE }

@Composable
private fun ScanFormatDialog(onPdf: () -> Unit, onJpg: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_choose_format)) },
        text = {
            Column {
                TextButton(onClick = onPdf) { Text(stringResource(R.string.export_format_pdf)) }
                TextButton(onClick = onJpg) { Text(stringResource(R.string.export_format_jpg)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) } },
    )
}

internal fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private suspend fun shareFile(context: Context, source: File?, name: String, mime: String) {
    val copy = source?.let { copyOrNull(context, it, name) }
    if (copy == null) {
        toast(context, R.string.export_failed)
    } else {
        val uri = FileProvider.getUriForFile(context, ExportFiles.authority(context), copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}

private suspend fun copyOrNull(context: Context, source: File, name: String): File? =
    withContext(Dispatchers.IO) {
        runCatching { ExportFiles.copyToExports(context, source, name) }.getOrNull()
    }

@StringRes
internal suspend fun saveTo(context: Context, source: File?, target: Uri): Int = withContext(Dispatchers.IO) {
    val copied = runCatching {
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            source?.inputStream()?.use { it.copyTo(out) }
        }
    }.getOrNull()
    if (source != null && copied == source.length()) R.string.export_saved else R.string.export_failed
}

private fun printFile(context: Context, source: File?, name: String) {
    val manager = context.getSystemService(PrintManager::class.java)
    if (source == null || manager == null) {
        toast(context, R.string.export_failed)
    } else {
        manager.print(name, FilePrintAdapter(source, name), null)
    }
}
