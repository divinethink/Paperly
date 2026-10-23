package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperlyFilterTest {
    private fun r(argb: Int) = (argb shr 16) and 0xFF
    private fun g(argb: Int) = (argb shr 8) and 0xFF

    @Test fun paperTurnsWhiteInkStaysDarkAndRedStaysRed() {
        val w = 20
        val px = IntArray(w * w) { 0xFFC8C8C8.toInt() } // shadowed grey paper
        px[5 * w + 5] = 0xFF303030.toInt() // ink
        px[12 * w + 12] = 0xFFC03030.toInt() // red pen
        val out = PaperlyFilter.apply(px, w, w)
        assertEquals(px.size, out.size)
        assertTrue(r(out[2 * w + 15]) > 245 && g(out[2 * w + 15]) > 245) // paper -> white
        assertTrue(r(out[5 * w + 5]) < 60) // ink dark
        assertTrue(r(out[12 * w + 12]) > g(out[12 * w + 12]) + 50) // still red
    }

    @Test fun handlesTinyImages() {
        assertEquals(1, PaperlyFilter.apply(intArrayOf(0xFF808080.toInt()), 1, 1).size)
        assertEquals(15, PaperlyFilter.apply(IntArray(15) { 0xFF000000.toInt() }, 5, 3).size)
    }
}
