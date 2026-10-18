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

    @Test fun newFilterNamesParse() {
        assertEquals(ScanFilter.DOCS, ScanFilter.parse("DOCS"))
        assertEquals(ScanFilter.AUTO, ScanFilter.parse("AUTO"))
        assertEquals(ScanFilter.PHOTO, ScanFilter.parse("PHOTO"))
    }

    @Test fun docsEvensOutShadowAndKeepsInk() {
        val w = 64
        val h = 64
        // paper gets darker left -> right (shadow); one dark ink pixel block in the middle
        val px = IntArray(w * h) {
            val shade = 230 - (it % w) * 2
            if (it % w in 30..33 && it / w in 30..33) 0xFF202020.toInt() else (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade
        }
        val out = LookFilters.docs(px, w, h)
        assertEquals(px.size, out.size)
        out.forEach { assertEquals(0xFF, it ushr 24) }
        val left = out[5 * w + 2] and 0xFF
        val right = out[5 * w + 61] and 0xFF
        assertTrue(left > 235 && right > 200) // both sides near white now (was 230 vs 108)
        assertTrue((out[31 * w + 31] and 0xFF) < 100) // ink stays dark
    }

    @Test fun docsHandlesTinyAndFlatImages() {
        assertEquals(1, LookFilters.docs(intArrayOf(0xFF808080.toInt()), 1, 1).size)
        assertEquals(15, LookFilters.docs(IntArray(15) { 0xFF000000.toInt() }, 5, 3).size)
    }

    @Test fun autoEnhanceRemovesColourCast() {
        // yellow-tinted paper (low blue) with dark ink
        val px = IntArray(100) { if (it < 10) 0xFF302010.toInt() else 0xFFE0D070.toInt() }
        val out = LookFilters.autoEnhance(px)
        assertEquals(100, out.size)
        val paper = out[50]
        assertEquals(paper shr 16 and 0xFF, paper and 0xFF) // R and B end up equal: cast gone
        assertTrue((out[0] and 0xFF) < 40)
        assertEquals(0, LookFilters.autoEnhance(IntArray(0)).size)
    }

    @Test fun photoBoostsColourKeepsGrey() {
        val grey = intArrayOf(0xFF808080.toInt())
        assertEquals(0x80, LookFilters.photo(grey)[0] and 0xFF)
        val red = intArrayOf(0xFFA06060.toInt())
        val out = LookFilters.photo(red)[0]
        assertTrue((out shr 16 and 0xFF) - (out and 0xFF) > 0x40) // red-blue gap grew from 0x40
        assertEquals(0xFF, out ushr 24)
    }

    @Test fun applyFilterToAllChangesOnlyTheFilter() {
        val locked = ScanEditPage(index = 1, locked = true, rotation = 90)
        val pages = listOf(ScanEditPage(index = 0), locked)
        val out = pages.withFilterAll(ScanFilter.DOCS)
        assertTrue(out.all { it.filter == ScanFilter.DOCS })
        assertEquals(90, out[1].rotation) // geometry of a locked page is untouched
        assertEquals(pages.map { it.index }, out.map { it.index })
    }

    @Test fun brightnessAndContrastAreClampedAndResettable() {
        val page = ScanEditPage(index = 0, locked = true)
        assertEquals(MAX_BRIGHTNESS, page.withBrightness(500).brightness)
        assertEquals(MIN_BRIGHTNESS, page.withBrightness(-500).brightness)
        assertEquals(MAX_CONTRAST, page.withContrast(500).contrast)
        assertEquals(MIN_CONTRAST, page.withContrast(-500).contrast)
        val tuned = page.withBrightness(20).withContrast(-10)
        assertTrue(tuned.locked) // adjustments work on locked pages (no geometry change)
        assertEquals(page, tuned.adjustReset())
    }

    @Test fun adjustIsIdentityAtZeroAndMovesPixels() {
        val px = intArrayOf(0xFF204060.toInt(), 0xFF808080.toInt(), 0xFFF0F0F0.toInt())
        assertTrue(LookFilters.adjust(px, 0, 0).contentEquals(px))
        val brighter = LookFilters.adjust(px, 50, 0)
        assertTrue((brighter[1] and 0xFF) > 0x80)
        val darker = LookFilters.adjust(px, -50, 0)
        assertTrue((darker[1] and 0xFF) < 0x80)
        val flat = LookFilters.adjust(px, 0, -50) // less contrast: the light pixel gets darker, the dark one lighter
        assertTrue((flat[2] and 0xFF) < 0xF0 && (flat[0] and 0xFF) > 0x60)
        val hard = LookFilters.adjust(px, 0, 100)
        assertEquals(0xFF, hard[2] ushr 24)
        assertEquals(0xFF, hard[2] and 0xFF) // light pixel saturates to white
    }
}
