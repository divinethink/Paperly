package com.paperly.app.feature.library

import androidx.lifecycle.ViewModel
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.data.export.PdfJpgRenderer
import com.paperly.app.data.export.PdfPageSubset
import com.paperly.app.data.export.PdfRecompressor
import com.paperly.app.domain.scanner.ExportSize
import com.paperly.app.domain.scanner.ScanPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val store: DocumentFileStore,
    private val quick: QuickViewStore,
    private val jpg: PdfJpgRenderer,
    private val subset: PdfPageSubset,
    private val recompressor: PdfRecompressor,
    private val pageInfo: ScanPageRepository,
) : ViewModel() {
    /** Library file, or the temporary copy of a quick-view PDF. */
    fun file(documentId: String): File? =
        if (QuickViewStore.isToken(documentId)) quick.resolve(documentId) else store.resolve(documentId)

    /** One JPG per page (or per page in [pages], 0-based; null = all); null result = failed. */
    suspend fun renderJpgs(
        documentId: String,
        base: String,
        pages: Set<Int>? = null,
        size: ExportSize = ExportSize.ACTUAL,
    ): List<File>? = file(documentId)?.let { jpg.render(it, base, pages, size) }

    /** PDF of [pages] (0-based; null = whole file) at [size]; ACTUAL = stored pages untouched; null = failed. */
    suspend fun sizedPdf(documentId: String, base: String, pages: Set<Int>?, size: ExportSize): File? {
        val whole = file(documentId) ?: return null
        val part = if (pages == null) whole else subset.extract(whole, pages, base) ?: return null
        return if (size == ExportSize.ACTUAL) part else recompressor.recompress(part, size, base)
    }

    /** Estimated bytes of the shared/saved file per size ([pdf] false = JPG images); empty when unknown. */
    suspend fun sizeEstimates(documentId: String, pages: Set<Int>?, pdf: Boolean): Map<ExportSize, Long> =
        file(documentId)?.let { recompressor.estimate(it, pages, pdf) }.orEmpty()

    /** A PDF made of only [pages] (0-based, document order) for printing; null = failed. */
    suspend fun pagesPdf(documentId: String, base: String, pages: Set<Int>): File? =
        file(documentId)?.let { subset.extract(it, pages, base) }

    /** The page's own title (0-based [index]) for use as the default file name; null = none, use the document name. */
    suspend fun pageName(documentId: String, index: Int?): String? =
        index?.let { pageInfo.observe(documentId, it).first().title.trim().ifBlank { null } }
}
