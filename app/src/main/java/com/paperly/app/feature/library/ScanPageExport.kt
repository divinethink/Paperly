package com.paperly.app.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.domain.document.Document
import kotlinx.coroutines.launch

/** What the page grid can do with the pages the user ticked (JPG pictures, except [print] = a PDF of those pages). */
internal class PageExport(
    val share: () -> Unit,
    val email: () -> Unit,
    val print: () -> Unit,
    val gallery: () -> Unit,
    val storage: () -> Unit,
)

/** Export actions for [chosen] (0-based pages) of the scanned document [doc]; the document itself is only read. */
@Composable
internal fun rememberPageExport(doc: Document, chosen: Set<Int>): PageExport {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ExportViewModel = hiltViewModel()
    val jpg = rememberJpgExport(doc.id, viewModel)
    val base = ExportFiles.baseName(doc.title)
    return PageExport(
        share = { jpg.share(base, chosen) },
        email = { jpg.email(base, chosen) },
        gallery = { jpg.gallery(base, chosen) },
        storage = { jpg.save(base, chosen) },
        print = {
            scope.launch {
                toast(context, R.string.export_jpg_preparing)
                printFile(context, viewModel.pagesPdf(doc.id, base, chosen), base)
            }
        },
    )
}
