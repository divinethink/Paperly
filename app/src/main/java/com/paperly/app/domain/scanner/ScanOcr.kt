package com.paperly.app.domain.scanner

import kotlinx.coroutines.flow.Flow

/** Recognition setup of the stored text; bump when it changes so old rows are simply read again. */
const val SCAN_OCR_ENGINE_VERSION = 1

/**
 * S3: optional background text recognition of scanned documents (Tesseract Bengali + English). The text is only a
 * helper (search, copy); the scan itself is never changed. Everything here is best-effort and never throws on purpose.
 */
interface ScanOcrRepository {
    /** The Settings switch (`scan_ocr_enabled`); OFF by default. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(value: Boolean)

    /** Queues recognition of one scan; does nothing while the switch is OFF. Repeated calls are harmless. */
    suspend fun schedule(documentId: String)

    /** Queues every scan that is not read yet (after the switch is turned ON and the data is on the device). */
    suspend fun scheduleAll()

    /** The pages changed: forget the stored text and read the scan again. */
    suspend fun invalidate(documentId: String)

    /** All recognised text of the scan, pages joined; null when nothing has been read yet. */
    suspend fun text(documentId: String): String?

    /** Ids of scans whose recognised text contains [query] (case-insensitive for Latin). Complete set, no limit. */
    fun observeMatchingIds(query: String): Flow<Set<String>>
}

/** Escapes `%`, `_` and `\` so a typed query is matched literally by `LIKE ... ESCAPE '\'`. */
fun escapeLike(query: String): String =
    buildString {
        for (c in query) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }

/** Joins per-page text (page index order) with a blank line; empty pages are dropped. Null when nothing is left. */
fun joinScanText(pages: List<Pair<Int, String>>): String? =
    pages.sortedBy { it.first }.map { it.second.trim() }.filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }?.joinToString("\n\n")
