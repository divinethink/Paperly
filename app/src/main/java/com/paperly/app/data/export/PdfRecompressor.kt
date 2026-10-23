package com.paperly.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.data.scanner.ScanPdfBuilder
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.scanner.ExportSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A smaller copy of a scanned PDF for Share / Save (every page re-encoded as a JPEG at the chosen [ExportSize]),
 * plus a cheap size estimate. The source file is only read; copies go to the cache `exports/sized/` folder (files
 * older than a day are removed). Never throws: null = failed.
 */
@Singleton
class PdfRecompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val builder: ScanPdfBuilder,
) {

    /** A new PDF with every page of [source] at [size]; null when anything failed (nothing is left behind). */
    suspend fun recompress(source: File, size: ExportSize, base: String): File? = withContext(Dispatchers.IO) {
        val dir = File(ExportFiles.exportsDir(context), "sized").apply { mkdirs() }
        ExportFiles.pruneOlderThan(dir, System.currentTimeMillis() - DAY_MS)
        val target = File(dir, "$base-${System.currentTimeMillis()}.pdf")
        val ok = guarded { engine -> write(engine, source, size, target) } == true
        if (ok && target.length() > 0) target else null.also { target.delete() }
    }

    /**
     * Estimated bytes per size for the 0-based [only] pages (null = all) of [source]: one middle page is encoded at
     * each size and multiplied by the page count. [pdf] = the PDF file is meant (ACTUAL = the stored size).
     */
    suspend fun estimate(source: File, only: Set<Int>?, pdf: Boolean): Map<ExportSize, Long> =
        withContext(Dispatchers.IO) {
            guarded { engine -> sizes(engine, source, only, pdf) } ?: emptyMap()
        }

    private suspend fun sizes(
        engine: PdfReaderEngine,
        source: File,
        only: Set<Int>?,
        pdf: Boolean,
    ): Map<ExportSize, Long> {
        val total = (engine.open(source) as? OpenResult.Success)?.pageCount ?: 0
        val wanted = (0 until total).filter { only == null || it in only }
        if (wanted.isEmpty()) return emptyMap()
        val sample = wanted[wanted.size / 2]
        val result = LinkedHashMap<ExportSize, Long>()
        for (size in ExportSize.entries) {
            val bytes = if (pdf && size == ExportSize.ACTUAL) {
                source.length() * wanted.size / total
            } else {
                (encode(engine, sample, size)?.size?.toLong() ?: return result) * wanted.size
            }
            result[size] = bytes
        }
        return result
    }

    private suspend fun write(engine: PdfReaderEngine, source: File, size: ExportSize, target: File): Boolean {
        val pages = (engine.open(source) as? OpenResult.Success)?.pageCount ?: 0
        if (pages == 0) return false
        PDFBoxResourceLoader.init(context)
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            for (index in 0 until pages) {
                val bitmap = render(engine, index, size) ?: return false
                try {
                    builder.addPage(doc, bitmap, size.jpegQuality)
                } finally {
                    bitmap.recycle()
                }
            }
            doc.save(target)
        }
        return true
    }

    private suspend fun encode(engine: PdfReaderEngine, index: Int, size: ExportSize): ByteArray? {
        val bitmap = render(engine, index, size) ?: return null
        return try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, size.jpegQuality, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun render(engine: PdfReaderEngine, index: Int, size: ExportSize): Bitmap? {
        val raw = engine.renderPage(index, size.widthPx) ?: return null
        val flat = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE) // JPEG has no transparency
            drawBitmap(raw, 0f, 0f, null)
        }
        raw.recycle()
        return flat
    }

    /** Runs [block] with a PDF engine that is always closed; null on any failure (not on cancellation). */
    private suspend fun <T> guarded(block: suspend (PdfReaderEngine) -> T): T? {
        val engine = PdfReaderEngine(context)
        return try {
            block(engine)
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: IOException) {
            null
        } catch (ignored: OutOfMemoryError) {
            null
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
