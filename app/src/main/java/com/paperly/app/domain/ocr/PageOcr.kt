package com.paperly.app.domain.ocr

import com.paperly.app.domain.reader.ReaderEngine
import kotlinx.coroutines.flow.StateFlow

/** Reads the text of a PDF page from its image (Bengali + English). Results are cached per document and page. */
interface PageOcr {
    /** True once the recognition data is on the device. */
    val modelReady: StateFlow<Boolean>

    /** One-time download of the recognition data; true when it is usable afterwards. */
    suspend fun downloadModel(): Boolean

    /** Text of page [index] (may be empty for a blank page); null when the data is missing or the page failed. */
    suspend fun pageText(documentId: String, engine: ReaderEngine, index: Int): String?

    /** Frees the recogniser's memory (Reader closed). Safe to call at any time; it is recreated on demand. */
    fun release()
}
