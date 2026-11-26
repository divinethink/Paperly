package com.paperly.app.domain.reader

import java.io.File

/** One annotation to put into the PDF file. [id] is our annotation id; it becomes the PDF `/NM` (dedupe key). */
data class PdfAnnotationItem(
    val id: String,
    val page: Int,
    val type: AnnotationType,
    val color: AnnotationColor?,
    val rects: List<MatchRect>,
    val noteText: String?,
    val stroke: AnnotationStroke? = null,
)

enum class PdfSkipReason { NOT_FOUND, TOO_LARGE, ENCRYPTED, OUT_OF_MEMORY }

sealed interface PdfWriteResult {
    /** File replaced. The caller MUST now update checksum + sizeBytes (+ sync state) of the document. */
    data class Written(val added: Int, val checksum: String, val sizeBytes: Long) : PdfWriteResult

    /** Every item was already in the file (or had no valid page): nothing changed. */
    data object NothingToWrite : PdfWriteResult

    /** Not written by design; the original is untouched and the highlight stays in the app only. */
    data class Skipped(val reason: PdfSkipReason) : PdfWriteResult

    /** Anything went wrong; the original is untouched. */
    data class Failed(val cause: Throwable) : PdfWriteResult
}

/**
 * Writes annotations into a PDF file as standard `/Highlight` `/Underline` `/StrikeOut` `/Text` annotations.
 * Safety: temp copy -> verify -> backup -> atomic replace; the original is never changed on any failure.
 * Callers must not have the file open (Reader closed or saving) and must serialize calls per file.
 */
interface PdfAnnotationWriter {
    /** [verify] = extra check of the finished temp file (e.g. opens in the viewer) before it replaces [file]. */
    suspend fun write(
        file: File,
        items: List<PdfAnnotationItem>,
        verify: suspend (File) -> Boolean = { true },
    ): PdfWriteResult

    /**
     * Like [write], but also keeps the file in step with edits: a mark of [items] that is already in the file and
     * changed (moved, resized, new colour/type/thickness/note) is replaced, and a mark whose id is in [removed]
     * (deleted in the app) is taken out. Any other mark in the file (other apps, or marks the app never knew) is
     * never touched. Only for the Library copy.
     */
    suspend fun mirror(
        file: File,
        items: List<PdfAnnotationItem>,
        removed: Set<String>,
        verify: suspend (File) -> Boolean = { true },
    ): PdfWriteResult = write(file, items, verify)
}
