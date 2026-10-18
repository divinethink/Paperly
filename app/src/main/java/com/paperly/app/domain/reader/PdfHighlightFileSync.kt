package com.paperly.app.domain.reader

import java.io.File

/** L3: asks for the app's highlights of a Library PDF to be written into the file (if the setting is on). */
interface PdfHighlightFileSync {
    /** Fire and forget (app scope, survives the Reader closing). The Reader must have closed the file first. */
    fun schedule(documentId: String)
}

/** The Library side of the write: which file may be written, and what to record once its bytes changed. */
interface LibraryPdfFiles {
    /** The stored file of a live (not trashed, downloaded) PDF/scanned-PDF document; null otherwise. */
    suspend fun writableFile(documentId: String): File?

    /** Same for an EPUB document (marks written into the book file); null otherwise. */
    suspend fun writableEpub(documentId: String): File? = null

    /** The file was replaced: record the new checksum/size and queue the cloud update. */
    suspend fun replaced(documentId: String, checksum: String, sizeBytes: Long)
}

/** Extra check of a finished temp PDF: does the viewer engine open it? */
fun interface PdfOpenCheck {
    suspend fun opens(file: File): Boolean
}

/**
 * Quick view: the Reader closed; puts the in-memory highlights into the ORIGINAL file if the setting is on and the
 * source allows writing, then always removes the temporary copy. Never throws into the UI.
 */
interface QuickPdfWriteBack {
    /**
     * Fire and forget (app scope). The Reader must have closed the temporary file first. If the original cannot be
     * written and [savedToLibrary] is false, the PDF is kept in the Library with its highlights instead.
     */
    fun finish(token: String, items: List<PdfAnnotationItem>, savedToLibrary: Boolean)

    /** A separate copy of the PDF with [items] written in (Save a copy / Share / Print); null = could not make it. */
    suspend fun markedCopy(token: String, items: List<PdfAnnotationItem>): File?

    /** Marks already inside the quick-view PDF (shown when it is reopened); empty if none. Never changes the file. */
    suspend fun existingMarks(token: String): List<PdfAnnotationItem>

    /** "Replace original with marks", right now. Returns the message (string resource) to show. */
    suspend fun replaceNow(token: String, items: List<PdfAnnotationItem>): Int
}
