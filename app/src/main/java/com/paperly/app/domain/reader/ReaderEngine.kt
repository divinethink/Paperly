package com.paperly.app.domain.reader

import android.graphics.Bitmap
import java.io.File

/** Capability flags: the UI shows a feature only when the engine supports it (Architecture §11). */
data class ReaderCapabilities(
    val supportsSearch: Boolean = false,
    val supportsTextSelection: Boolean = false,
    val supportsReflow: Boolean = false,
    val supportsOutline: Boolean = false,
    val supportsAnnotation: Boolean = false,
)

/** Search-hit rectangle as fractions (0..1) of the page width/height, origin top-left: zoom-independent. */
data class MatchRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Text picked by a drag between two page points: line rectangles (page fractions, origin top-left) + plain text. */
data class TextSelection(val rects: List<MatchRect>, val text: String)

sealed interface OpenResult {
    data class Success(val pageCount: Int) : OpenResult
    data object PasswordRequired : OpenResult
    data object Failed : OpenResult
}

/** One engine instance = one open document. Callers must [close] it when done. */
interface ReaderEngine {
    val capabilities: ReaderCapabilities
    suspend fun open(file: File, password: String? = null): OpenResult

    /** Page width/height ratio of page [index]; null if unknown. */
    suspend fun pageAspect(index: Int): Float?

    /** Renders page [index] (0-based) scaled to at most [maxWidthPx] wide; null on any failure. */
    suspend fun renderPage(index: Int, maxWidthPx: Int): Bitmap?

    /** Plain text of page [index]; null if unavailable (no text layer or failure). */
    suspend fun pageText(index: Int): String?

    /** Zero-based page -> hit rectangles (may be empty if unknown) for [query], ascending by page; null on failure. */
    suspend fun search(query: String): Map<Int, List<MatchRect>>?

    /** Text between two page points given as 0..1 page fractions; null if unsupported, no text there, or failure. */
    suspend fun selectText(page: Int, x1: Float, y1: Float, x2: Float, y2: Float): TextSelection? = null

    fun close()
}
