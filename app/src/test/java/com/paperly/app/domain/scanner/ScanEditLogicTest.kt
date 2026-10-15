package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEditLogicTest {
    @Test fun filterParseFallsBackToOriginal() {
        assertEquals(ScanFilter.BW, ScanFilter.parse("BW"))
        assertEquals(ScanFilter.ORIGINAL, ScanFilter.parse("nope"))
        assertEquals(ScanFilter.ORIGINAL, ScanFilter.parse(null))
    }

    @Test fun rotationStaysInQuarterTurns() {
        assertEquals(90, rotateRight(0))
        assertEquals(0, rotateRight(270))
        assertEquals(270, normalizeRotation(-90))
        assertEquals(0, normalizeRotation(720))
    }

    @Test fun annotatedPageBlocksCropAndRotateOnly() {
        assertFalse(canCropOrRotate(true))
        assertTrue(canCropOrRotate(false))
    }

    @Test fun quadJsonRoundTrips() {
        val q = ScanQuad(ScanPoint(0.1f, 0.1f), ScanPoint(0.9f, 0.15f), ScanPoint(0.85f, 0.9f), ScanPoint(0.05f, 0.8f))
        assertEquals(q, ScanQuad.fromJson(q.toJson()))
        assertEquals(ScanQuad.FULL, ScanQuad.fromJson(ScanQuad.FULL.toJson()))
    }

    @Test fun badQuadJsonIsNull() {
        assertNull(ScanQuad.fromJson(null))
        assertNull(ScanQuad.fromJson("[1,2,3]"))
        assertNull(ScanQuad.fromJson("[a,b,c,d,e,f,g,h]"))
        assertNull(ScanQuad.fromJson("[0,0,0,0,0,0,0,0]")) // degenerate
        assertNull(ScanQuad.fromJson("[0,0,2,0,1,1,0,1]")) // outside the image
        assertNull(ScanQuad.fromJson("[0,0,1,1,1,0,0,1]")) // bow-tie (not convex)
    }

    @Test fun outputSizeScalesDownNeverUp() {
        assertEquals(1000 to 2000, ScanQuad.outputSize(ScanQuad.FULL, 1000, 2000, 5000))
        assertEquals(500 to 1000, ScanQuad.outputSize(ScanQuad.FULL, 1000, 2000, 1000))
    }

    @Test fun grayAndColorKeepSizeAndOpaque() {
        val px = intArrayOf(0xFF204060.toInt(), 0xFFE0E0E0.toInt(), 0xFF808080.toInt(), 0xFF000000.toInt())
        val g = PixelFilters.gray(px)
        assertEquals(4, g.size)
        g.forEach {
            assertEquals(0xFF, it ushr 24)
            assertEquals((it shr 16) and 0xFF, it and 0xFF)
        }
    }

    @Test fun enhanceColorWhitensDimPaper() {
        val px = IntArray(100) { if (it < 10) 0xFF303030.toInt() else 0xFFB0B0B0.toInt() }
        val out = PixelFilters.enhanceColor(px)
        assertEquals(0xFF, out[50] and 0xFF) // paper becomes white
        assertEquals(0x00, out[0] and 0xFF) // ink becomes black
    }

    @Test fun enhanceColorLeavesFlatImageAlone() {
        val px = IntArray(25) { 0xFF909090.toInt() }
        assertTrue(PixelFilters.enhanceColor(px).contentEquals(px))
    }

    @Test fun blackWhiteIgnoresUnevenLight() {
        // left half dim paper, right half bright paper, one dark "ink" pixel on each side
        val w = 64
        val h = 64
        val px = IntArray(w * h) { if (it % w < w / 2) 0xFF909090.toInt() else 0xFFF0F0F0.toInt() }
        px[10 * w + 10] = 0xFF101010.toInt()
        px[10 * w + 50] = 0xFF303030.toInt()
        val out = PixelFilters.blackWhite(px, w, h)
        assertEquals(0x00, out[10 * w + 10] and 0xFF)
        assertEquals(0x00, out[10 * w + 50] and 0xFF)
        assertEquals(0xFF, out[30 * w + 5] and 0xFF) // dim paper still white
        assertEquals(0xFF, out[30 * w + 60] and 0xFF)
    }

    @Test fun blackWhiteHandlesLargeSumsWithoutOverflowErrors() {
        val w = 3000
        val h = 1500 // integral sums exceed 2^31 -> relies on wrap-around being harmless
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        px[5 * w + 5] = 0xFF000000.toInt()
        val out = PixelFilters.blackWhite(px, w, h)
        assertEquals(0x00, out[5 * w + 5] and 0xFF)
        assertEquals(0xFF, out[h * w - 1] and 0xFF)
    }

    @Test fun lockedPageKeepsRotationAndCropButTakesFilter() {
        val page = ScanEditPage(index = 0, locked = true)
        assertEquals(page, page.rotated())
        assertEquals(
            page,
            page.withQuad(ScanQuad(ScanPoint(0.1f, 0.1f), ScanPoint(0.9f, 0.1f), ScanPoint(0.9f, 0.9f), ScanPoint(0.1f, 0.9f))),
        )
        assertEquals(ScanFilter.BW, page.withFilter(ScanFilter.BW).filter)
    }

    @Test fun freePageRotatesAndRejectsInvalidCrop() {
        val page = ScanEditPage(index = 0)
        assertEquals(90, page.rotated().rotation)
        assertEquals(page, page.withQuad(ScanQuad(ScanPoint(0f, 0f), ScanPoint(0f, 0f), ScanPoint(0f, 0f), ScanPoint(0f, 0f))))
    }
}
