package com.paperly.app.domain.scanner

import java.io.File

/**
 * One page in the editor. [index] = the kept source page it came from; an added page (S4, [added] = its staged file
 * name) has [ADDED_INDEX]. Its position in the editor list is its position in the PDF.
 * [locked] = the page carries highlights/annotations, so crop and rotation are frozen (they would move the page
 * fractions the annotations are stored in) and it cannot be deleted; filters and moving stay allowed.
 */
data class ScanEditPage(
    val index: Int,
    val rotation: Int = 0,
    val quad: ScanQuad = ScanQuad.FULL,
    val filter: ScanFilter = ScanFilter.ORIGINAL,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val locked: Boolean = false,
    val added: String? = null,
) {
    val isAdded: Boolean get() = added != null
}

const val ADDED_INDEX = -1

/** Edit operations; a [ScanEditPage.locked] page keeps its geometry (crop/rotation), filters always apply. */
fun ScanEditPage.rotated(): ScanEditPage = if (locked) this else copy(rotation = rotateRight(rotation))

fun ScanEditPage.withFilter(value: ScanFilter): ScanEditPage = copy(filter = value)

/** Manual brightness / contrast (clamped to their ranges); like filters they never touch geometry. */
fun ScanEditPage.withBrightness(value: Int): ScanEditPage =
    copy(brightness = value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS))

fun ScanEditPage.withContrast(value: Int): ScanEditPage =
    copy(contrast = value.coerceIn(MIN_CONTRAST, MAX_CONTRAST))

fun ScanEditPage.adjustReset(): ScanEditPage = copy(brightness = 0, contrast = 0)

const val MIN_BRIGHTNESS = -100
const val MAX_BRIGHTNESS = 100
const val MIN_CONTRAST = -50
const val MAX_CONTRAST = 100

/** Same filter on every page (filters never touch geometry, so locked pages take it too). */
fun List<ScanEditPage>.withFilterAll(value: ScanFilter): List<ScanEditPage> = map { it.withFilter(value) }

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

    /** S4: image of any editor page (kept source or staged added page); null if missing. */
    fun pageFile(documentId: String, page: ScanEditPage): File?

    /** S4: copies new pictures (scanner pages or gallery picks) aside for adding; returns [ScanEditPage]s or null. */
    suspend fun stageAdded(documentId: String, uris: List<String>): List<ScanEditPage>?

    /** S4: removes the staged (not yet saved) added pages; call when the editor closes. */
    fun discardStaged(documentId: String)

    /** Gallery import: unedited PDF of the picked images (+ their kept copies). Null on failure. */
    suspend fun stageGallery(uris: List<String>): StagedScan?
}

/** Feature flag `scan_editor_enabled` (default ON). OFF = previous behaviour: ML Kit PDF only, no editor/gallery. */
interface ScanEditorSettings {
    val enabled: kotlinx.coroutines.flow.Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
