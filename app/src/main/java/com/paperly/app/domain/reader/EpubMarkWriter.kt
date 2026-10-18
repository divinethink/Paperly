package com.paperly.app.domain.reader

import java.io.File

/**
 * Writes EPUB marks into the book file itself (see [EpubXhtmlMarks]), so any other reader shows them.
 * Safety is the same as for PDF: temp copy -> verify -> backup (7 days) -> atomic replace; any failure leaves the
 * file untouched. Reuses [PdfWriteResult] (Written carries the new checksum/size the caller must record).
 * Callers must not have the file open and must serialize calls per file.
 */
interface EpubMarkWriter {
    /**
     * Adds [marks] not yet in the file. [removeStale] = also take out our marks that are no longer in [marks]
     * (Library books, where the database is the truth); false for a book opened from outside.
     */
    suspend fun write(
        file: File,
        marks: List<EpubAnnotation>,
        removeStale: Boolean,
        verify: suspend (File) -> Boolean = { true },
    ): PdfWriteResult
}

/** Extra check of a finished temp EPUB: does the reader library open it? */
fun interface EpubOpenCheck {
    suspend fun opens(file: File): Boolean
}
