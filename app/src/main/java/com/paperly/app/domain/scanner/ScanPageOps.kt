package com.paperly.app.domain.scanner

/** S4 page management on the editor list (pure logic, no Android). Position in the list = position in the PDF. */

/** Moves the page at [from] to [to]; out-of-range or no-op returns the list unchanged. */
fun List<ScanEditPage>.moved(from: Int, to: Int): List<ScanEditPage> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { it.add(to, it.removeAt(from)) }
}

/** A page may be deleted unless it carries highlights ([ScanEditPage.locked]) or is the last page. */
fun List<ScanEditPage>.canDeleteAt(position: Int): Boolean =
    size > 1 && getOrNull(position)?.locked == false

fun List<ScanEditPage>.withoutAt(position: Int): List<ScanEditPage> =
    if (canDeleteAt(position)) filterIndexed { i, _ -> i != position } else this

/** True when order, count or content of pages changed (not only filters/crop/rotation of the same pages). */
fun List<ScanEditPage>.isStructural(storedCount: Int): Boolean =
    size != storedCount || any { it.isAdded } || indices.any { this[it].index != it }

/** Kept page index -> its new position. Deleted pages and added pages are not in the map. */
fun List<ScanEditPage>.positionMap(): Map<Int, Int> =
    withIndex().filter { !it.value.isAdded }.associate { it.value.index to it.index }

/**
 * Is [pages] a safe plan for a document that stored [stored]? Kept pages must be distinct and exist, every locked
 * (highlighted) stored page must still be there, added pages need a distinct staged name, and the PDF is not empty.
 */
fun validPlan(pages: List<ScanEditPage>, stored: List<ScanEditPage>): Boolean {
    if (pages.isEmpty()) return false
    val kept = pages.filter { !it.isAdded }.map { it.index }
    val added = pages.filter { it.isAdded }.map { it.added.orEmpty() }
    val keptOk = kept.toSet().size == kept.size && kept.all { it in stored.indices }
    val lockedOk = stored.filter { it.locked }.all { it.index in kept }
    val addedOk = added.toSet().size == added.size && added.all { it.isNotBlank() } &&
        pages.filter { it.isAdded }.all { it.index == ADDED_INDEX && !it.locked }
    return keptOk && lockedOk && addedOk
}
