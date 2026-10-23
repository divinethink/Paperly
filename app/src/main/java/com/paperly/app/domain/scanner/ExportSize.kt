package com.paperly.app.domain.scanner

/**
 * File-size choice when a scan is shared or saved. [ACTUAL] = the stored file as it is (for JPG: the usual 1600 px /
 * quality 90 page images); the others re-encode every page at [widthPx] and JPEG [jpegQuality] (smaller file).
 */
enum class ExportSize(val widthPx: Int, val jpegQuality: Int) {
    ACTUAL(ACTUAL_WIDTH, ACTUAL_QUALITY),
    MEDIUM(MEDIUM_WIDTH, MEDIUM_QUALITY),
    SMALL(SMALL_WIDTH, SMALL_QUALITY),
    SMALLEST(SMALLEST_WIDTH, SMALLEST_QUALITY),
}

private const val ACTUAL_WIDTH = 1600
private const val ACTUAL_QUALITY = 90
private const val MEDIUM_WIDTH = 1000
private const val MEDIUM_QUALITY = 65
private const val SMALL_WIDTH = 800
private const val SMALL_QUALITY = 55
private const val SMALLEST_WIDTH = 600
private const val SMALLEST_QUALITY = 45
