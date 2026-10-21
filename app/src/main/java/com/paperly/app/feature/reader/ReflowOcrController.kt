package com.paperly.app.feature.reader

import android.graphics.Bitmap
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.reader.ReaderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OcrDownloadUi(val running: Boolean = false, val failed: Boolean = false)

/** Reflow text for pages with no usable text layer: OCR of the page image (P10), plus the one-time data download. */
class ReflowOcrController(
    private val scope: CoroutineScope,
    private val ocr: PageOcr,
    private val engine: () -> ReaderEngine?,
    private val documentId: String,
) {
    val modelReady: StateFlow<Boolean> = ocr.modelReady
    private val downloadState = MutableStateFlow(OcrDownloadUi())
    val download: StateFlow<OcrDownloadUi> = downloadState.asStateFlow()

    fun startDownload() {
        if (downloadState.value.running) return
        downloadState.value = OcrDownloadUi(running = true)
        scope.launch {
            val ok = ocr.downloadModel()
            downloadState.value = OcrDownloadUi(running = false, failed = !ok)
        }
    }

    /** The original page image, so a wrong reading can be checked; null = could not be drawn. */
    suspend fun pageImage(index: Int, widthPx: Int): Bitmap? = engine()?.renderPage(index, widthPx)

    /** Text of one page; null = could not be read. */
    suspend fun pageText(index: Int): String? = engine()?.let { ocr.pageText(documentId, it, index) }

    /**
     * Text of page [index] for the "Copy" action of a page without a text layer. Fetches the recognition data first
     * when it is missing ([onDownloading] tells the owner). Null = data not available or nothing could be read.
     */
    suspend fun copyPageText(index: Int, onDownloading: () -> Unit): String? {
        val ready = ocr.modelReady.value || run {
            onDownloading()
            ocr.downloadModel()
        }
        return if (ready) pageText(index)?.trim()?.takeIf { it.isNotEmpty() } else null
    }
}
