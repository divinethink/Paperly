package com.paperly.app.domain.scanner

/** Step F: copying/moving pages to another scanned document (pure logic, no Android). */

/** The ticked [positions] as editor pages in page order; null when none is ticked or a position does not exist. */
fun List<ScanEditPage>.picked(positions: Set<Int>): List<ScanEditPage>? =
    if (positions.isEmpty() || positions.any { it !in indices }) null else positions.sorted().map { this[it] }

/**
 * The page as a staged (added) page of another document. Crop, rotation, filter, brightness and contrast come along;
 * highlights do not, so the copy is never locked.
 */
fun ScanEditPage.copiedAs(stagedName: String): ScanEditPage =
    copy(index = ADDED_INDEX, added = stagedName, locked = false)

/** The page as page [position] of a new document whose kept pictures are the copies (same settings, not locked). */
fun ScanEditPage.copiedAt(position: Int): ScanEditPage = copy(index = position, added = null, locked = false)

/** True when the page differs from its plain original picture (crop, rotation, filter, brightness, contrast). */
fun ScanEditPage.isEdited(): Boolean =
    rotation != 0 || quad != ScanQuad.FULL || filter != ScanFilter.ORIGINAL || brightness != 0 || contrast != 0
