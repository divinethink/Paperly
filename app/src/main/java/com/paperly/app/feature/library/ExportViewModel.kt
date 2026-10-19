package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.data.export.PdfJpgRenderer
import com.paperly.app.data.export.PdfPageSubset
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val store: DocumentFileStore,
    private val quick: QuickViewStore,
    private val jpg: PdfJpgRenderer,
    private val subset: PdfPageSubset,
) : ViewModel() {
    /** Library file, or the temporary copy of a quick-view PDF. */
    fun file(documentId: String): File? =
        if (QuickViewStore.isToken(documentId)) quick.resolve(documentId) else store.resolve(documentId)

    /** One JPG per page (or per page in [pages], 0-based; null = all); null result = failed. */
    suspend fun renderJpgs(documentId: String, base: String, pages: Set<Int>? = null): List<File>? =
        file(documentId)?.let { jpg.render(it, base, pages) }

    /** A PDF made of only [pages] (0-based, document order) for printing; null = failed. */
    suspend fun pagesPdf(documentId: String, base: String, pages: Set<Int>): File? =
        file(documentId)?.let { subset.extract(it, pages, base) }
}
