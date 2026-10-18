package com.paperly.app.domain.reader

import java.io.File

/** Id prefix of a mark that was read out of the PDF file (not created in this session): shown, never written again. */
const val FILE_MARK_PREFIX = "file:"

fun isFileMark(id: String): Boolean = id.startsWith(FILE_MARK_PREFIX)

/**
 * Reads the standard markup annotations (`/Highlight` `/Underline` `/Squiggly` `/StrikeOut` `/Text`) back out of a
 * PDF file, so marks written by us (or another app) are shown again when the file is reopened. Read-only: the file is
 * never changed. Any problem (encrypted, too large, damaged) gives an empty list, never an exception.
 */
interface PdfAnnotationReader {
    suspend fun read(file: File): List<PdfAnnotationItem>
}
