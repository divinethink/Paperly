package com.paperly.app.feature.library

import android.content.Context
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.scanner.ExportSize
import java.io.File
import kotlinx.coroutines.launch

private const val PDF_MIME = "application/pdf"
private const val EMAIL_MIME = "message/rfc822"

/** Share, Send email and Save to device ask file name, PDF or JPG and file size in one dialog first. */
private enum class PageSend(@StringRes val label: Int) {
    SHARE(R.string.export_action_share),
    EMAIL(R.string.export_action_email),
    SAVE(R.string.export_action_save),
}

/**
 * Export actions for [chosen] (0-based pages) of the scanned document [doc]; the document itself is only read.
 * [onDone] runs once an action has started, so the grid can leave selection mode.
 */
@Composable
internal fun rememberPageExport(doc: Document, chosen: Set<Int>, onDone: () -> Unit): PageExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ExportViewModel = hiltViewModel()
    val jpg = rememberJpgExport(doc.id, viewModel)
    val base = ExportFiles.baseName(doc.title)
    var ask by remember { mutableStateOf<PageSend?>(null) }
    var firstName by remember { mutableStateOf(base) }
    var pdfReady by remember { mutableStateOf<File?>(null) }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        if (uri != null) scope.launch { toast(context, saveTo(context, pdfReady, uri)) }
    }
    val send = ask
    if (send != null) {
        ScanFormatDialog(
            initialName = firstName,
            confirmLabel = send.label,
            estimate = { pdf -> viewModel.sizeEstimates(doc.id, chosen, pdf) },
            onConfirm = { name, format, size ->
                ask = null
                val clean = ExportFiles.baseName(name)
                when (format) {
                    ScanFormat.PDF -> scope.launch {
                        val file = preparedPdf(context, viewModel, doc.id, clean, chosen, size)
                        if (send == PageSend.SAVE && file != null) {
                            pdfReady = file
                            pdfSaver.launch("$clean.pdf")
                        } else {
                            sharePagesPdf(context, file, clean, send == PageSend.EMAIL)
                        }
                    }
                    ScanFormat.JPG -> sendJpgs(jpg, send, clean, chosen, size)
                }
                onDone()
            },
            onDismiss = { ask = null },
        )
    }
    val prompt: (PageSend) -> Unit = { action ->
        scope.launch {
            firstName = singlePageName(viewModel, doc, chosen)
            ask = action
        }
    }
    return PageExport(
        share = { prompt(PageSend.SHARE) },
        email = { prompt(PageSend.EMAIL) },
        save = { prompt(PageSend.SAVE) },
        print = {
            scope.launch { printPages(context, viewModel, doc.id, base, chosen) }
            onDone()
        },
    )
}

/** One page chosen and it has its own title: that title is the default file name; otherwise the document name. */
private suspend fun singlePageName(viewModel: ExportViewModel, doc: Document, chosen: Set<Int>): String =
    viewModel.pageName(doc.id, chosen.singleOrNull())?.let { ExportFiles.baseName(it) }
        ?: ExportFiles.baseName(doc.title)

private suspend fun sharePagesPdf(context: Context, file: File?, name: String, email: Boolean) =
    shareFile(context, file, "$name.pdf", if (email) EMAIL_MIME else PDF_MIME)

private suspend fun preparedPdf(
    context: Context,
    viewModel: ExportViewModel,
    docId: String,
    name: String,
    chosen: Set<Int>,
    size: ExportSize,
): File? {
    toast(context, R.string.export_pdf_preparing)
    return viewModel.sizedPdf(docId, name, chosen, size)
}

private fun sendJpgs(jpg: ScanJpgExport, send: PageSend, name: String, chosen: Set<Int>, size: ExportSize) =
    when (send) {
        PageSend.SHARE -> jpg.share(name, chosen, size)
        PageSend.EMAIL -> jpg.email(name, chosen, size)
        PageSend.SAVE -> jpg.save(name, chosen, size)
    }

private suspend fun printPages(
    context: Context,
    viewModel: ExportViewModel,
    docId: String,
    base: String,
    chosen: Set<Int>,
) {
    toast(context, R.string.export_jpg_preparing)
    printFile(context, viewModel.pagesPdf(docId, base, chosen), base)
}
