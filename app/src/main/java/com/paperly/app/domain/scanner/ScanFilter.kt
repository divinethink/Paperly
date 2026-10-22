package com.paperly.app.domain.scanner

/** S2 page filters. [parse] never throws: an unknown/missing name (older/newer data) falls back to ORIGINAL. */
enum class ScanFilter {
    ORIGINAL,
    COLOR,
    GRAY,
    BW,
    DOCS,
    AUTO,
    PHOTO,
    CLEAN,
    SEPIA,
    ;

    companion object {
        fun parse(name: String?): ScanFilter = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

/**
 * Filters shown as thumbnails. B/W is a separate toggle button; COLOR/GRAY/PHOTO/AUTO stay in the enum (older pages
 * keep their look) but are no longer offered.
 */
val SCAN_FILTER_CHOICES: List<ScanFilter> =
    listOf(ScanFilter.ORIGINAL, ScanFilter.DOCS, ScanFilter.CLEAN, ScanFilter.SEPIA)

/** B/W button: not B/W -> BW; already B/W -> back to the filter it replaced ([before]), else ORIGINAL. */
fun toggledBw(current: ScanFilter, before: ScanFilter?): ScanFilter =
    if (current == ScanFilter.BW) before?.takeIf { it != ScanFilter.BW } ?: ScanFilter.ORIGINAL else ScanFilter.BW

/** Output size of a rebuilt PDF: longest page side in pixels and JPEG quality. */
enum class ScanOutputSize(val maxSide: Int, val jpegQuality: Int) {
    SMALL(SMALL_SIDE, SMALL_QUALITY),
    MEDIUM(MEDIUM_SIDE, MEDIUM_QUALITY),
    LARGE(LARGE_SIDE, LARGE_QUALITY),
}

private const val SMALL_SIDE = 1280
private const val SMALL_QUALITY = 60
private const val MEDIUM_SIDE = 1754
private const val MEDIUM_QUALITY = 75
private const val LARGE_SIDE = 2480
private const val LARGE_QUALITY = 85

/** Rotation in degrees clockwise, always one of 0/90/180/270. */
fun normalizeRotation(degrees: Int): Int = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

fun rotateRight(degrees: Int): Int = normalizeRotation(degrees + QUARTER_TURN)

fun rotateLeft(degrees: Int): Int = normalizeRotation(degrees - QUARTER_TURN)

private const val QUARTER_TURN = 90
private const val FULL_TURN = 360

/**
 * Risk rule (roadmap S2): highlights/annotations are stored as page fractions, so a page that has any must keep its
 * geometry. Filters stay allowed.
 */
fun canCropOrRotate(pageHasAnnotations: Boolean): Boolean = !pageHasAnnotations
