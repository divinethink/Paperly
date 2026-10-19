package com.paperly.app.feature.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
internal fun rememberDocumentExport(
    doc: Document,
    marked: (suspend () -> File?)? = null,
    onSaved: () -> Unit = {},
): DocumentExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ExportViewModel = hiltViewModel()
    val name = ExportFiles.fileName(doc.title, doc.type)
    val mime = ExportFiles.mimeType(doc.type)

    // [marked] = a quick-view PDF with marks: Save / Share / Print then use a copy that has them (null result = failed)
    suspend fun fileOf(): File? = if (marked != null) marked() else viewModel.file(doc.id)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) {
            scope.launch {
                val message = saveTo(context, fileOf(), uri)
                toast(context, message)
                if (message == R.string.export_saved) onSaved()
            }
        }
    }
    val askImages = rememberImagesAsk(doc, viewModel)
    val isPdf = ExportFiles.isPdf(doc.type)
    val isScan = doc.type == SCAN_TYPE
    val sharePdf: (String) -> Unit = { shareName -> scope.launch { shareFile(context, fileOf(), shareName, mime) } }
    val scan = rememberScanChoice(doc, viewModel, sharePdf) { saver.launch(it) }
    return DocumentExport(
        share = { if (isScan) scan.share() else sharePdf(name) },
        saveCopy = { if (isScan) scan.save() else saver.launch(name) },
        print = if (isPdf) {
            { scope.launch { printFile(context, fileOf(), name) } }
        } else {
            null
        },
        exportImages = if (isPdf) askImages else null,
    )
}

/** "Export as images" question (JPG or PNG); returns the action that opens it. */
@Composable
private fun rememberImagesAsk(doc: Document, viewModel: ExportViewModel): () -> Unit {
    val context = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    if (ask) {
        ImageFormatDialog(
            onPick = { png ->
                ask = false
                viewModel.file(doc.id)?.let { enqueueImageExport(context, it, ExportFiles.baseName(doc.title), png) }
            },
            onDismiss = { ask = false },
        )
    }
    return { ask = true }
}

/** Scan Share / Save a copy: asks PDF or JPG first; JPG renders the pages and shares/saves them right away. */
private class ScanChoice(val share: () -> Unit, val save: () -> Unit)

@Composable
private fun rememberScanChoice(
    doc: Document,
    viewModel: ExportViewModel,
    sharePdf: (String) -> Unit,
    savePdf: (String) -> Unit,
): ScanChoice {
    var ask by remember { mutableStateOf<ScanAction?>(null) }
    val jpg = rememberJpgExport(doc.id, viewModel)
    ScanFormatPrompt(
        action = ask,
        doc = doc,
        viewModel = viewModel,
        onChoose = { action, format, base ->
            val share = action == ScanAction.SHARE
            when (format) {
                ScanFormat.PDF -> ExportFiles.fileName(base, doc.type).let { if (share) sharePdf(it) else savePdf(it) }
                ScanFormat.JPG -> ExportFiles.baseName(base).let { if (share) jpg.share(it) else jpg.save(it) }
            }
        },
        onClose = { ask = null },
    )
    return ScanChoice(share = { ask = ScanAction.SHARE }, save = { ask = ScanAction.SAVE })
}

private const val SCAN_TYPE = "scanned-pdf"

private enum class ScanAction { SHARE, SAVE }

private enum class ScanFormat { PDF, JPG }

@Composable
private fun ScanFormatPrompt(
    action: ScanAction?,
    doc: Document,
    viewModel: ExportViewModel,
    onChoose: (ScanAction, ScanFormat, String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val size = remember(doc.id, action != null) {
        val length = if (action == null) null else viewModel.file(doc.id)?.length()
        length?.let { Formatter.formatShortFileSize(context, it) }
    }
    if (action == null) return
    ScanFormatDialog(
        initialName = ExportFiles.baseName(doc.title),
        sizeText = size,
        onPdf = { name ->
            onClose()
            onChoose(action, ScanFormat.PDF, name)
        },
        onJpg = { name ->
            onClose()
            onChoose(action, ScanFormat.JPG, name)
        },
        onDismiss = onClose,
    )
}

/** PDF or JPG, with an editable file name (made safe by [ExportFiles.baseName]) and the current PDF size. */
@Composable
private fun ScanFormatDialog(
    initialName: String,
    sizeText: String?,
    onPdf: (String) -> Unit,
    onJpg: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_choose_format)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.export_file_name)) },
                    singleLine = true,
                )
                if (sizeText != null) Text(stringResource(R.string.export_file_size, sizeText))
                TextButton(onClick = { onPdf(name) }) { Text(stringResource(R.string.export_format_pdf)) }
                TextButton(onClick = { onJpg(name) }) { Text(stringResource(R.string.export_format_jpg)) }
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
