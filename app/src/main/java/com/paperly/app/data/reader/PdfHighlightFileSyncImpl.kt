package com.paperly.app.data.reader

import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfHighlightFileSync
import com.paperly.app.domain.reader.PdfOpenCheck
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
 * L3 step 3: after the Reader closed a Library PDF, writes its highlights into the file (setting on), then records
 * the new checksum/size and queues the cloud update. One write at a time. Every failure leaves the original file
 * and the app's own highlights untouched (the writer's guarantee), so nothing here is allowed to throw into the UI.
 */
@Singleton
class PdfHighlightFileSyncImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val files: LibraryPdfFiles,
    private val annotations: AnnotationRepository,
    private val writer: PdfAnnotationWriter,
    private val openCheck: PdfOpenCheck,
    private val settings: PdfFileWriteSettings,
) : PdfHighlightFileSync {
    private val lock = Mutex()

    override fun schedule(documentId: String) {
        if (documentId.isEmpty()) return
        scope.launch { runCatching { writeNow(documentId) } }
    }

    /** Null = not attempted (setting off, or not a writable Library PDF). */
    internal suspend fun writeNow(documentId: String): PdfWriteResult? = lock.withLock { attempt(documentId) }

    private suspend fun attempt(documentId: String): PdfWriteResult? {
        if (!settings.writeHighlights.first()) return null
        val file = files.writableFile(documentId) ?: return null
        val items = annotations.observe(documentId).first()
            .map { PdfAnnotationItem(it.id, it.page, it.type, it.color, it.rects, it.noteText) }
        val result = writer.write(file, items) { openCheck.opens(it) }
        if (result is PdfWriteResult.Written) files.replaced(documentId, result.checksum, result.sizeBytes)
        return result
    }
}
