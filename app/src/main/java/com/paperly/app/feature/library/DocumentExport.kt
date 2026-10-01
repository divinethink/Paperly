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
import java.io.File
import java.io.IOException
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
    return DocumentExport(
        share = { scope.launch { shareFile(context, viewModel.file(doc.id), name, mime) } },
        saveCopy = { saver.launch(name) },
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
        try {
            ExportFiles.copyToExports(context, source, name)
        } catch (e: IOException) {
            null
        }
    }

@StringRes
internal suspend fun saveTo(context: Context, source: File?, target: Uri): Int = withContext(Dispatchers.IO) {
    val copied = try {
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            source?.inputStream()?.use { it.copyTo(out) }
        }
    } catch (e: IOException) {
        null
    }
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
