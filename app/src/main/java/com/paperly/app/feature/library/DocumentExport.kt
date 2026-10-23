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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.scanner.ExportSize
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
    var sizedPdf by remember { mutableStateOf<File?>(null) } // scan Save: the copy made at the chosen size
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) {
            scope.launch {
                val message = saveTo(context, sizedPdf ?: fileOf(), uri)
                sizedPdf = null
                toast(context, message)
                if (message == R.string.export_saved) onSaved()
            }
        }
    }
    val askImages = rememberImagesAsk(doc, viewModel)
    val isPdf = ExportFiles.isPdf(doc.type)
    val isScan = doc.type == SCAN_TYPE
    val sharePdf: (String) -> Unit = { shareName -> scope.launch { shareFile(context, fileOf(), shareName, mime) } }
    val scan = rememberScanChoice(doc, viewModel) { file, fileName ->
        sizedPdf = file
        saver.launch(fileName)
    }
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

/** Scan Share / Save a copy: one dialog asks name, PDF or JPG and file size; JPG renders the pages right away. */
private class ScanChoice(val share: () -> Unit, val save: () -> Unit)

@Composable
private fun rememberScanChoice(
    doc: Document,
    viewModel: ExportViewModel,
    savePdf: (file: File, fileName: String) -> Unit,
): ScanChoice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf<ScanAction?>(null) }
    val jpg = rememberJpgExport(doc.id, viewModel)
    ScanFormatPrompt(
        action = ask,
        doc = doc,
        viewModel = viewModel,
        onChoose = { action, format, name, size ->
            val share = action == ScanAction.SHARE
            when (format) {
                ScanFormat.PDF -> scope.launch {
                    toast(context, R.string.export_pdf_preparing)
                    val file = viewModel.sizedPdf(doc.id, ExportFiles.baseName(name), null, size)
                    val fileName = ExportFiles.fileName(name, doc.type)
                    if (!share && file != null) {
                        savePdf(file, fileName)
                    } else {
                        shareFile(context, file, fileName, ExportFiles.mimeType(doc.type))
                    }
                }
                ScanFormat.JPG -> ExportFiles.baseName(name).let {
                    if (share) jpg.share(it, null, size) else jpg.save(it, null, size)
                }
            }
        },
        onClose = { ask = null },
    )
    return ScanChoice(share = { ask = ScanAction.SHARE }, save = { ask = ScanAction.SAVE })
}

private const val SCAN_TYPE = "scanned-pdf"

private enum class ScanAction(@StringRes val label: Int) {
    SHARE(R.string.export_action_share),
    SAVE(R.string.export_action_save),
}

@Composable
private fun ScanFormatPrompt(
    action: ScanAction?,
    doc: Document,
    viewModel: ExportViewModel,
    onChoose: (ScanAction, ScanFormat, String, ExportSize) -> Unit,
    onClose: () -> Unit,
) {
    if (action == null) return
    ScanFormatDialog(
        initialName = ExportFiles.baseName(doc.title),
        confirmLabel = action.label,
        estimate = { pdf -> viewModel.sizeEstimates(doc.id, null, pdf) },
        onConfirm = { name, format, size ->
            onClose()
            onChoose(action, format, name, size)
        },
        onDismiss = onClose,
    )
}

internal fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

internal suspend fun shareFile(context: Context, source: File?, name: String, mime: String) {
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

internal fun printFile(context: Context, source: File?, name: String) {
    val manager = context.getSystemService(PrintManager::class.java)
    if (source == null || manager == null) {
        toast(context, R.string.export_failed)
    } else {
        manager.print(name, FilePrintAdapter(source, name), null)
    }
}
