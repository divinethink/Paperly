package com.paperly.app.feature.library

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
import kotlinx.coroutines.launch

private const val PDF_MIME = "application/pdf"
private const val EMAIL_MIME = "message/rfc822"

/** Share and Send email ask PDF or JPG (with an editable file name) first, like the whole-document Share. */
private enum class PageSend { SHARE, EMAIL }

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
                    val mime = if (send == PageSend.SHARE) PDF_MIME else EMAIL_MIME
                    shareFile(context, file, ExportFiles.baseName(name) + ".pdf", mime)
                }
                onDone()
            },
            onJpg = { name ->
                ask = null
                val jpgBase = ExportFiles.baseName(name)
                if (send == PageSend.SHARE) jpg.share(jpgBase, chosen) else jpg.email(jpgBase, chosen)
                onDone()
            },
            onDismiss = { ask = null },
        )
    }
    return PageExport(
        share = { ask = PageSend.SHARE },
        email = { ask = PageSend.EMAIL },
        gallery = {
            jpg.gallery(base, chosen)
            onDone()
        },
        storage = {
            jpg.save(base, chosen)
            onDone()
        },
        print = {
            scope.launch {
                toast(context, R.string.export_jpg_preparing)
                printFile(context, viewModel.pagesPdf(doc.id, base, chosen), base)
            }
            onDone()
        },
    )
}
