package com.paperly.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.reader.ReaderCapabilities
import com.paperly.app.domain.reader.ReaderEngine
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

    override val capabilities = ReaderCapabilities(supportsSearch = true)
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

    override suspend fun searchPages(query: String): List<Int>? {
        val doc = document ?: return null
        return try {
            val hits = doc.searchDocument(query, 0 until doc.pageCount)
            (0 until hits.size()).map { hits.keyAt(it) }.sorted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
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
