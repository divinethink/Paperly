package com.paperly.app.domain.scanner

import java.io.File

/**
 * One page in the editor. [locked] = the page carries highlights/annotations, so crop and rotation are frozen
 * (they would move the page fractions the annotations are stored in); filters stay allowed.
 */
data class ScanEditPage(
    val index: Int,
    val rotation: Int = 0,
    val quad: ScanQuad = ScanQuad.FULL,
    val filter: ScanFilter = ScanFilter.ORIGINAL,
    val locked: Boolean = false,
)

/** Edit operations; a [ScanEditPage.locked] page keeps its geometry (crop/rotation), filters always apply. */
fun ScanEditPage.rotated(): ScanEditPage = if (locked) this else copy(rotation = rotateRight(rotation))

fun ScanEditPage.withFilter(value: ScanFilter): ScanEditPage = copy(filter = value)

fun ScanEditPage.withQuad(value: ScanQuad): ScanEditPage = if (locked || !value.isValid()) this else copy(quad = value)

fun ScanEditPage.cropReset(): ScanEditPage = withQuad(ScanQuad.FULL)

enum class ScanEditResult { SAVED, FAILED, OUT_OF_MEMORY }

/** Pages of a gallery import, staged in app cache; feed them into the normal scan save flow. */
data class StagedScan(val pdfUri: String, val pageUris: List<String>)

/** S2: page editor for scanned documents whose source pages were kept (`files/scans/<documentId>/`). */
interface ScanEditRepository {
    /** Keeps the scanner's page images for later editing. Best effort: failure only means "not editable later". */
    suspend fun storeSources(documentId: String, pageUris: List<String>)

    /** Editor state of every page, or null when this document cannot be edited (no kept sources). */
    suspend fun load(documentId: String): List<ScanEditPage>?

    /**
     * Rebuilds the PDF from the sources with [pages]. Safety: temp file -> verify (opens, page count) -> backup ->
     * atomic replace; any failure leaves the original file untouched.
     */
    suspend fun apply(documentId: String, pages: List<ScanEditPage>, size: ScanOutputSize): ScanEditResult

    /** The kept (un-edited) image of a page, for previews; null if missing. */
    fun sourceFile(documentId: String, index: Int): File?

    /** Gallery import: unedited PDF of the picked images (+ their kept copies). Null on failure. */
    suspend fun stageGallery(uris: List<String>): StagedScan?
}

/** Feature flag `scan_editor_enabled` (default ON). OFF = previous behaviour: ML Kit PDF only, no editor/gallery. */
interface ScanEditorSettings {
    val enabled: kotlinx.coroutines.flow.Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
