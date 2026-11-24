package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLookQualityTest {
    private val white = 0xFFFFFFFF.toInt()
    private fun r(argb: Int) = (argb shr 16) and 0xFF

    @Test fun lessBrightKeepsWhitePaperWhiteButDarkensInk() {
        val out = LookFilters.adjust(intArrayOf(white, 0xFF808080.toInt(), 0xFF303030.toInt()), -17, 4)
        assertEquals(255, r(out[0])) // paper stays pure white
        assertTrue(r(out[1]) < 0x80) // mid grey gets darker
        assertTrue(r(out[2]) < 0x30) // ink gets darker
    }

    @Test fun moreBrightStillLightensEverything() {
        val out = LookFilters.adjust(intArrayOf(0xFF808080.toInt()), 20, 0)
        assertTrue(r(out[0]) > 0x80)
    }

    @Test fun zeroAdjustmentIsUnchanged() {
        val px = intArrayOf(0xFF123456.toInt(), white)
        assertEquals(px.toList(), LookFilters.adjust(px, 0, 0).toList())
    }

    @Test fun sharpenLeavesFlatAreasAndWhiteAlone() {
        val w = 12
        val px = IntArray(w * w) { white }
        val before = px.copyOf()
        Sharpen.apply(px, w, w)
        assertEquals(before.toList(), px.toList())
    }

    @Test fun sharpenMakesAnEdgeCrisper() {
        val w = 12
        // left half grey 120 (ink side), right half 230 (paper side): the edge pixels move apart
        val px = IntArray(w * w) { if (it % w < w / 2) 0xFF787878.toInt() else 0xFFE6E6E6.toInt() }
        Sharpen.apply(px, w, w)
        val row = 5 * w
        assertTrue(r(px[row + w / 2 - 1]) < 120) // ink side of the edge darker
        assertTrue(r(px[row + w / 2]) > 230) // paper side lighter
        assertEquals(120, r(px[row])) // far from the edge: unchanged
    }

    @Test fun sharpenKeepsColourHueAndHandlesTinyImages() {
        val one = intArrayOf(0xFFC03030.toInt())
        Sharpen.apply(one, 1, 1)
        assertEquals(0xFFC03030.toInt(), one[0])
        Sharpen.apply(IntArray(15) { 0xFF000000.toInt() }, 5, 3)
    }

    @Test fun maxSizeOnlyOnBigHeapAndFallsBackToLarge() {
        assertEquals(ScanOutputSize.LARGE, outputSizeForHeap(256L * 1024 * 1024))
        assertEquals(ScanOutputSize.MAX, outputSizeForHeap(512L * 1024 * 1024))
        assertEquals(ScanOutputSize.LARGE, ScanOutputSize.MAX.saferOnMemory())
        assertNull(ScanOutputSize.LARGE.saferOnMemory())
        assertNull(ScanOutputSize.SMALL.saferOnMemory())
        assertEquals(3508, ScanOutputSize.MAX.maxSide)
    }
}
