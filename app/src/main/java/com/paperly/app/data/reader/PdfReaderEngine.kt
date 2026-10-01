package com.paperly.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.util.Size
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.content.PdfPageTextContent
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderCapabilities
import com.paperly.app.domain.reader.ReaderEngine
import com.paperly.app.domain.reader.TextSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers

/** PDF engine on `androidx.pdf` document-service (sandboxed process). Text + search (P2-E); reflow P3. */
class PdfReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderEngine {

    override val capabilities = ReaderCapabilities(supportsSearch = true, supportsTextSelection = true)
    private var document: PdfDocument? = null

    override suspend fun open(file: File, password: String?): OpenResult {
        close()
        return try {
            val doc = SandboxedPdfLoader(context, Dispatchers.IO).openDocument(Uri.fromFile(file), password)
            document = doc
            OpenResult.Success(doc.pageCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPasswordError(e)) OpenResult.PasswordRequired else OpenResult.Failed
        }
    }

    override suspend fun pageAspect(index: Int): Float? {
        val doc = document ?: return null
        return try {
            val info = doc.getPageInfo(index)
            if (info.width > 0 && info.height > 0) info.width.toFloat() / info.height else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun renderPage(index: Int, maxWidthPx: Int): Bitmap? {
        val doc = document ?: return null
        return try {
            val info = doc.getPageInfo(index)
            check(info.width > 0 && info.height > 0) { "invalid page size" }
            val source = doc.getPageBitmapSource(index)
            try {
                source.getBitmap(fitSize(info.width, info.height, maxWidthPx), null)
            } finally {
                source.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun pageText(index: Int): String? {
        val doc = document ?: return null
        return try {
            doc.getPageContent(index)?.textContents?.joinToString("\n") { it.text }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): Map<Int, List<MatchRect>>? {
        val doc = document ?: return null
        return try {
            val hits = doc.searchDocument(query, 0 until doc.pageCount)
            val result = LinkedHashMap<Int, List<MatchRect>>() // SparseArray keys are ascending
            for (i in 0 until hits.size()) {
                val page = hits.keyAt(i)
                val boxes = hits.valueAt(i).flatMap { it.bounds }
                result[page] = normalized(doc, page, boxes)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun selectText(page: Int, x1: Float, y1: Float, x2: Float, y2: Float): TextSelection? {
        val doc = document ?: return null
        return try {
            val info = doc.getPageInfo(page)
            val w = info.width.toFloat()
            val h = info.height.toFloat()
            val picked = if (w > 0f && h > 0f) {
                doc.getSelectionBounds(page, PointF(x1 * w, y1 * h), PointF(x2 * w, y2 * h))
            } else {
                null
            }
            picked?.let { toSelection(it.selectedContents, w, h) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Page points -> 0..1 fractions. A page whose size is unknown keeps its match but loses the rectangles. */
    private suspend fun normalized(doc: PdfDocument, page: Int, boxes: List<RectF>): List<MatchRect> {
        val info = try {
            doc.getPageInfo(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        if (info.width <= 0 || info.height <= 0) return emptyList()
        val w = info.width.toFloat()
        val h = info.height.toFloat()
        return boxes.map { MatchRect(it.left / w, it.top / h, it.right / w, it.bottom / h) }
            .filter { it.right > it.left && it.bottom > it.top }
    }

    override fun close() {
        runCatching { document?.close() }
        document = null
    }

    // Typed password exception not yet confirmed for beta01; match by name until verified on device (technical debt).
    private fun isPasswordError(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH)
            .any { it.javaClass.simpleName.contains("Password", ignoreCase = true) }

    private fun fitSize(width: Int, height: Int, maxWidthPx: Int): Size {
        val scale = min(maxWidthPx.toFloat() / width, MAX_BITMAP_HEIGHT_PX.toFloat() / height)
        return Size((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }

    private companion object {
        const val MAX_BITMAP_HEIGHT_PX = 4096
        const val MAX_CAUSE_DEPTH = 4
    }
}

/** Text parts of a page selection -> line rectangles as 0..1 page fractions + joined text; null if nothing usable. */
private fun toSelection(contents: List<*>, w: Float, h: Float): TextSelection? {
    val parts = contents.filterIsInstance<PdfPageTextContent>()
    val rects = parts.flatMap { it.bounds }
        .map { MatchRect(it.left / w, it.top / h, it.right / w, it.bottom / h) }
        .filter { it.right > it.left && it.bottom > it.top }
    return if (rects.isEmpty()) null else TextSelection(rects, parts.joinToString(" ") { it.text })
}
