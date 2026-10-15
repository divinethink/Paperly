package com.paperly.app.data.scanner

import android.content.Context
import com.paperly.app.core.database.ScanOcrDao
import com.paperly.app.core.database.ScanOcrEntity
import com.paperly.app.data.ocr.OcrModel
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.scanner.SCAN_OCR_ENGINE_VERSION
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanOcrRun { DONE, SKIPPED, RETRY }

/** What identifies the file's current bytes cheaply: a replaced file changes at least one of these. */
private data class FileStamp(val modified: Long, val length: Long)

private fun File.stamp() = FileStamp(lastModified(), length())

/**
 * Reads the pages of one stored scan with OCR and keeps the text in `scan_ocr`. Resumable: pages already stored
 * for this engine version are skipped, each page is saved as soon as it is read. If the file is replaced while a
 * page is being read (page edit, highlight write) that text is dropped and the run is retried, so stored text
 * always belongs to the current file. The PDF is only read, never changed.
 */
@Singleton
class ScanOcrRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: LibraryPdfFiles,
    private val ocr: PageOcr,
    private val dao: ScanOcrDao,
) {
    suspend fun run(documentId: String): ScanOcrRun {
        val file = files.writableFile(documentId)
        if (file == null || !ocr.modelReady.value) return ScanOcrRun.SKIPPED
        val engine = PdfReaderEngine(context)
        return try {
            val pages = (engine.open(file) as? OpenResult.Success)?.pageCount ?: return ScanOcrRun.SKIPPED
            readPages(documentId, file, engine, pages)
        } finally {
            engine.close()
            ocr.release()
        }
    }

    private suspend fun readPages(documentId: String, file: File, engine: PdfReaderEngine, pages: Int): ScanOcrRun {
        val stamp = file.stamp()
        val done = dao.donePages(documentId, SCAN_OCR_ENGINE_VERSION).toSet()
        var failed = false
        for (i in 0 until pages) {
            if (i in done) continue
            val text = ocr.freshText(engine, i)
            if (file.stamp() != stamp) return ScanOcrRun.RETRY // replaced meanwhile: this text may not match
            if (text == null) failed = true else dao.upsert(row(documentId, i, text))
        }
        return if (failed) ScanOcrRun.RETRY else ScanOcrRun.DONE
    }

    private fun row(documentId: String, index: Int, text: String) =
        ScanOcrEntity(documentId, index, text, OcrModel.LANG_SPEC, SCAN_OCR_ENGINE_VERSION, System.currentTimeMillis())
}
