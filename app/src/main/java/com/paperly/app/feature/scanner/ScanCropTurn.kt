package com.paperly.app.feature.scanner

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.scanner.ScanPoint

/** Crop step with a turned page: the frame stays in the stored picture's fractions, these map to what is shown. */
internal const val QUARTER_TURN = 90
internal const val HALF_TURN = 180
internal const val THREE_QUARTER_TURN = 270

/** A point of the stored picture (fractions) as it appears in the turned picture. */
internal fun toShown(p: ScanPoint, rotation: Int): ScanPoint = when (rotation) {
    QUARTER_TURN -> ScanPoint(1f - p.y, p.x)
    HALF_TURN -> ScanPoint(1f - p.x, 1f - p.y)
    THREE_QUARTER_TURN -> ScanPoint(p.y, 1f - p.x)
    else -> p
}

/** A finger drag over the turned picture (fractions of what is shown) as a drag over the stored picture. */
internal fun toStoredDrag(d: Offset, rotation: Int): Offset = when (rotation) {
    QUARTER_TURN -> Offset(d.y, -d.x)
    HALF_TURN -> Offset(-d.x, -d.y)
    THREE_QUARTER_TURN -> Offset(-d.y, d.x)
    else -> d
}
