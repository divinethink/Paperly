package com.paperly.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import com.googlecode.tesseract.android.TessBaseAPI
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderEngine
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LANG = "ben"
private const val SAMPLE_PAGES = 3
private const val RENDER_WIDTH = 2000
private const val PREVIEW_CHARS = 400
private const val MB = 1024L * 1024L

data class OcrPageResult(val page: Int, val millis: Long, val chars: Int, val confidence: Int, val preview: String)

data class OcrSpikeReport(val pageCount: Int, val pages: List<OcrPageResult>, val nativeMb: Long, val error: String?) {
    fun toText(): String = error ?: buildString {
        append("PDF pages: $pageCount | native memory +$nativeMb MB\n")
        pages.forEach {
            append("\n— Page ${it.page + 1}: ${it.millis} ms, ${it.chars} chars, confidence ${it.confidence}%\n")
            append(it.preview).append('\n')
        }
    }
}

/** P10 spike (temporary): Bengali OCR of the first pages of a PDF, to judge quality, speed and memory. */
object OcrSpike {
    suspend fun run(context: Context, pdf: Uri): OcrSpikeReport = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "ocr-spike.pdf")
        val engine = PdfReaderEngine(context)
        val api = TessBaseAPI()
        try {
            copy(context, pdf, file)
            recognise(context, engine, api, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            failed("OCR trial failed: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
        } finally {
            api.recycle()
            engine.close()
            file.delete()
        }
    }

    private fun failed(message: String) = OcrSpikeReport(0, emptyList(), 0, message)

    private fun copy(context: Context, from: Uri, to: File) {
        val input = checkNotNull(context.contentResolver.openInputStream(from)) { "cannot read the PDF" }
        input.use { src -> to.outputStream().use { src.copyTo(it) } }
    }

    private suspend fun recognise(
        context: Context,
        engine: ReaderEngine,
        api: TessBaseAPI,
        file: File,
    ): OcrSpikeReport {
        val count = (engine.open(file) as? OpenResult.Success)?.pageCount
            ?: return failed("The PDF did not open (password-protected or damaged).")
        if (!api.init(OcrModel.dataDir(context).absolutePath, LANG)) return failed("OCR engine could not start.")
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        val before = Debug.getNativeHeapAllocatedSize()
        val pages = (0 until minOf(count, SAMPLE_PAGES)).map { ocrPage(engine, api, it) }
        val grown = (Debug.getNativeHeapAllocatedSize() - before) / MB
        return OcrSpikeReport(count, pages, grown, null)
    }

    private suspend fun ocrPage(engine: ReaderEngine, api: TessBaseAPI, index: Int): OcrPageResult {
        val bitmap = engine.renderPage(index, RENDER_WIDTH)?.copy(Bitmap.Config.ARGB_8888, false)
            ?: return OcrPageResult(index, 0, 0, 0, "(page could not be drawn)")
        val start = SystemClock.elapsedRealtime()
        api.setImage(bitmap)
        val text = api.getUTF8Text().orEmpty().trim()
        val confidence = api.meanConfidence()
        val millis = SystemClock.elapsedRealtime() - start
        bitmap.recycle()
        return OcrPageResult(index, millis, text.length, confidence, text.take(PREVIEW_CHARS))
    }
}
