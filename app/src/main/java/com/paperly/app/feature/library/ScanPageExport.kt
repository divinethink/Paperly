package com.paperly.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.File
import kotlinx.coroutines.launch

private const val PDF_MIME = "application/pdf"
private const val EMAIL_MIME = "message/rfc822"

/** Share, Send email and Save to device ask PDF or JPG (with an editable file name) first. */
private enum class PageSend { SHARE, EMAIL, SAVE }

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
    var pdfReady by remember { mutableStateOf<File?>(null) }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
        if (uri != null) scope.launch { toast(context, saveTo(context, pdfReady, uri)) }
    }
    val send = ask
    if (send != null) {
        ScanFormatDialog(
            initialName = base,
            sizeText = null,
            onPdf = { name ->
                ask = null
                scope.launch {
                    toast(context, R.string.export_pdf_preparing)
                    val file = viewModel.pagesPdf(doc.id, ExportFiles.baseName(name), chosen)
                    val fileName = ExportFiles.baseName(name) + ".pdf"
                    if (send == PageSend.SAVE) {
                        pdfReady = file
                        pdfSaver.launch(fileName)
                    } else {
                        shareFile(context, file, fileName, if (send == PageSend.SHARE) PDF_MIME else EMAIL_MIME)
                    }
                }
                onDone()
            },
            onJpg = { name ->
                ask = null
                val jpgBase = ExportFiles.baseName(name)
                when (send) {
                    PageSend.SHARE -> jpg.share(jpgBase, chosen)
                    PageSend.EMAIL -> jpg.email(jpgBase, chosen)
                    PageSend.SAVE -> jpg.save(jpgBase, chosen)
                }
                onDone()
            },
            onDismiss = { ask = null },
        )
    }
    return PageExport(
        share = { ask = PageSend.SHARE },
        email = { ask = PageSend.EMAIL },
        save = { ask = PageSend.SAVE },
        print = {
            scope.launch {
                toast(context, R.string.export_jpg_preparing)
                printFile(context, viewModel.pagesPdf(doc.id, base, chosen), base)
            }
            onDone()
        },
    )
}
