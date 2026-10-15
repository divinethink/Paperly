package com.paperly.app.data.reader

import android.net.Uri
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfHighlightFileSync
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The original could not be changed: the untouched copy goes into the Library, the highlights into its database, and
 * the normal Library write puts them into that file. Returns false if the PDF could not be kept.
 */
@Singleton
class QuickLibraryFallback @Inject constructor(
    private val store: QuickViewStore,
    private val repository: DocumentRepository,
    private val annotations: AnnotationRepository,
    private val fileSync: PdfHighlightFileSync,
) {
    suspend fun keep(token: String, pristine: File, items: List<PdfAnnotationItem>): Boolean {
        val title = store.document(token)?.title ?: "Document"
        val id = when (val r = repository.importQuick(Uri.fromFile(pristine).toString(), title, DocumentType.PDF)) {
            is ImportResult.Success -> r.documentId
            is ImportResult.Duplicate -> r.existingId
            else -> null
        } ?: return false
        items.forEach { a ->
            val rect = a.rects.firstOrNull() ?: return@forEach
            annotations.add(id, a.page, rect, AnnotationContent(a.type, a.color, a.noteText), a.rects)
        }
        fileSync.schedule(id)
        return true
    }
}
