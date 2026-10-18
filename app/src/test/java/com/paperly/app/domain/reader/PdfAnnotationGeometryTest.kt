package com.paperly.app.domain.reader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfAnnotationGeometryTest {
    private val rect = MatchRect(0.1f, 0.1f, 0.5f, 0.2f)

    private fun page(rotation: Int, left: Float = 0f, bottom: Float = 0f) =
        PdfPageBox(left, bottom, 600f, 800f, rotation)

    private fun assertQuad(q: PdfQuad, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        assertEquals(minX, q.minX, DELTA)
        assertEquals(minY, q.minY, DELTA)
        assertEquals(maxX, q.maxX, DELTA)
        assertEquals(maxY, q.maxY, DELTA)
    }

    @Test
    fun rotation0FlipsYAxis() = assertQuad(PdfAnnotationGeometry.toQuad(rect, page(0)), 60f, 640f, 300f, 720f)

    @Test
    fun rotation90() = assertQuad(PdfAnnotationGeometry.toQuad(rect, page(90)), 60f, 80f, 120f, 400f)

    @Test
    fun rotation180() = assertQuad(PdfAnnotationGeometry.toQuad(rect, page(180)), 300f, 80f, 540f, 160f)

    @Test
    fun rotation270() = assertQuad(PdfAnnotationGeometry.toQuad(rect, page(270)), 480f, 400f, 540f, 720f)

    @Test
    fun cropBoxOffsetIsAdded() =
        assertQuad(PdfAnnotationGeometry.toQuad(rect, page(0, left = 10f, bottom = 20f)), 70f, 660f, 310f, 740f)

    @Test
    fun oddRotationsAreNormalized() {
        assertEquals(270, PdfAnnotationGeometry.normalizeRotation(-90))
        assertEquals(90, PdfAnnotationGeometry.normalizeRotation(450))
        assertEquals(0, PdfAnnotationGeometry.normalizeRotation(45))
        assertQuad(PdfAnnotationGeometry.toQuad(rect, page(-90)), 480f, 400f, 540f, 720f)
    }

    @Test
    fun outOfRangeFractionsAreClamped() {
        val q = PdfAnnotationGeometry.toQuad(MatchRect(-1f, -1f, 2f, 2f), page(0))
        assertQuad(q, 0f, 0f, 600f, 800f)
    }

    @Test
    fun toRectIsTheInverseOfToQuadForAllRotations() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val box = page(rotation, left = 10f, bottom = 20f)
            val back = PdfAnnotationGeometry.toRect(PdfAnnotationGeometry.toQuad(rect, box), box)
            assertEquals(rect.left, back.left, DELTA_FRACTION)
            assertEquals(rect.top, back.top, DELTA_FRACTION)
            assertEquals(rect.right, back.right, DELTA_FRACTION)
            assertEquals(rect.bottom, back.bottom, DELTA_FRACTION)
        }
    }

    @Test
    fun nearestColorMapsEveryPaletteColorToItself() {
        AnnotationColor.entries.forEach { color ->
            val (r, g, b) = PdfAnnotationColors.rgb(color)
            assertEquals(color, PdfAnnotationColors.nearest(r, g, b))
        }
        assertEquals(AnnotationColor.AMBER, PdfAnnotationColors.nearest(1f, 0.9f, 0.1f))
    }

    @Test
    fun quadPointsUseTopLeftTopRightBottomLeftBottomRight() {
        val q = PdfQuad(1f, 2f, 3f, 4f)
        assertArrayEquals(floatArrayOf(1f, 4f, 3f, 4f, 1f, 2f, 3f, 2f), q.quadPoints(), 0f)
    }

    @Test
    fun boundsCoverAllParts() {
        val b = PdfAnnotationGeometry.bounds(listOf(PdfQuad(1f, 2f, 3f, 4f), PdfQuad(0f, 5f, 2f, 9f)))
        assertQuad(b, 0f, 2f, 3f, 9f)
    }

    @Test
    fun colorsAreUnitRgb() {
        val amber = PdfAnnotationColors.rgb(AnnotationColor.AMBER)
        assertEquals(1f, amber[0], DELTA)
        assertEquals(0xB3 / 255f, amber[1], DELTA)
        assertEquals(0f, amber[2], DELTA)
        AnnotationColor.entries.forEach { c -> PdfAnnotationColors.rgb(c).forEach { assertEquals(true, it in 0f..1f) } }
    }

    private companion object {
        const val DELTA = 0.01f
        const val DELTA_FRACTION = 0.0001f
    }
}
