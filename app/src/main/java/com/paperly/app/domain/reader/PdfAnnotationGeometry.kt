package com.paperly.app.domain.reader

import kotlin.math.max
import kotlin.math.min

/**
 * Visible page area in PDF user space (CropBox clipped to MediaBox, origin bottom-left) plus the page's `/Rotate`.
 * Pure data: no PDF library here (domain stays SDK-free).
 */
data class PdfPageBox(val left: Float, val bottom: Float, val width: Float, val height: Float, val rotation: Int)

/** One highlighted part in PDF user space (axis-aligned, origin bottom-left). */
data class PdfQuad(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    /** `/QuadPoints` of one part, in the order every viewer accepts: TL, TR, BL, BR. */
    fun quadPoints(): FloatArray = floatArrayOf(minX, maxY, maxX, maxY, minX, minY, maxX, minY)
}

/**
 * Our page fractions (0..1, origin top-left of the page AS DISPLAYED: after /Rotate and CropBox) -> PDF user space.
 * Pure maths, JVM-tested for all four rotations.
 */
object PdfAnnotationGeometry {
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTERS = 270
    private const val FULL = 360
    private const val ONE = 1f

    /** `/Rotate` can be negative or > 360 in the wild; anything that is not a multiple of 90 is treated as 0. */
    fun normalizeRotation(raw: Int): Int {
        val r = ((raw % FULL) + FULL) % FULL
        return if (r % QUARTER == 0) r else 0
    }

    fun toQuad(rect: MatchRect, page: PdfPageBox): PdfQuad {
        val (x1, y1) = point(rect.left, rect.top, page)
        val (x2, y2) = point(rect.right, rect.bottom, page)
        return PdfQuad(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    /** Bounding box of several parts (the annotation's `/Rect`). [quads] must not be empty. */
    fun bounds(quads: List<PdfQuad>): PdfQuad = PdfQuad(
        quads.minOf { it.minX },
        quads.minOf { it.minY },
        quads.maxOf { it.maxX },
        quads.maxOf { it.maxY },
    )

    /** u = fraction from the displayed left edge, v = fraction from the displayed top edge. */
    private fun point(rawU: Float, rawV: Float, p: PdfPageBox): Pair<Float, Float> {
        val u = rawU.coerceIn(0f, ONE)
        val v = rawV.coerceIn(0f, ONE)
        return when (normalizeRotation(p.rotation)) {
            QUARTER -> Pair(p.left + v * p.width, p.bottom + u * p.height)
            HALF -> Pair(p.left + (ONE - u) * p.width, p.bottom + v * p.height)
            THREE_QUARTERS -> Pair(p.left + (ONE - v) * p.width, p.bottom + (ONE - u) * p.height)
            else -> Pair(p.left + u * p.width, p.bottom + (ONE - v) * p.height)
        }
    }
}

/** RGB (0..1) written into the PDF; same palette as the on-screen swatches (`AnnotationColorPicker`). */
object PdfAnnotationColors {
    private const val BYTE_MAX = 255f
    private const val BYTE_MASK = 0xFF
    private const val GREEN_SHIFT = 8
    private const val RED_SHIFT = 16
    private const val BLACK_HEX = 0x212121
    private const val PURPLE_HEX = 0x8E24AA
    private const val BLUE_HEX = 0x1E88E5
    private const val GREEN_HEX = 0x388E3C
    private const val RED_HEX = 0xD32F2F
    private const val AMBER_HEX = 0xFFB300

    fun rgb(color: AnnotationColor): FloatArray {
        val hex = when (color) {
            AnnotationColor.AMBER -> AMBER_HEX
            AnnotationColor.RED -> RED_HEX
            AnnotationColor.GREEN -> GREEN_HEX
            AnnotationColor.BLUE -> BLUE_HEX
            AnnotationColor.PURPLE -> PURPLE_HEX
            AnnotationColor.BLACK -> BLACK_HEX
        }
        return floatArrayOf(
            ((hex shr RED_SHIFT) and BYTE_MASK) / BYTE_MAX,
            ((hex shr GREEN_SHIFT) and BYTE_MASK) / BYTE_MAX,
            (hex and BYTE_MASK) / BYTE_MAX,
        )
    }
}
