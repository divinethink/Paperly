package com.paperly.app.data.reader

import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.EpubHighlightFileSync
import com.paperly.app.domain.reader.EpubMarkWriter
import com.paperly.app.domain.reader.EpubOpenCheck
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.PdfWriteResult
import com.paperly.app.domain.settings.PdfFileWriteSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * After the Reader closed a Library EPUB: writes its marks into the file (setting on; the database is the truth, so
 * deleted marks leave the file too), then records the new checksum/size and queues the cloud update. One write at
 * a time; every failure leaves the book and the app's own marks untouched, so nothing here may throw into the UI.
 */
@Singleton
class EpubHighlightFileSyncImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val files: LibraryPdfFiles,
    private val annotations: EpubAnnotationRepository,
    private val writer: EpubMarkWriter,
    private val openCheck: EpubOpenCheck,
    private val settings: PdfFileWriteSettings,
) : EpubHighlightFileSync {
    private val lock = Mutex()

    override fun schedule(documentId: String) {
        if (documentId.isEmpty()) return
        scope.launch { runCatching { writeNow(documentId) } }
    }

    /** Null = not attempted (setting off, or not a writable Library EPUB). */
    internal suspend fun writeNow(documentId: String): PdfWriteResult? = lock.withLock { attempt(documentId) }

    private suspend fun attempt(documentId: String): PdfWriteResult? {
        if (!settings.writeHighlights.first()) return null
        val file = files.writableEpub(documentId) ?: return null
        val marks = annotations.observe(documentId).first()
        val result = writer.write(file, marks, removeStale = true) { openCheck.opens(it) }
        if (result is PdfWriteResult.Written) files.replaced(documentId, result.checksum, result.sizeBytes)
        return result
    }
}
