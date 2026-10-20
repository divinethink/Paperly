package com.paperly.app.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfEdgeScrollTest {
    @Test
    fun middleOfTheScreenDoesNotScroll() {
        assertEquals(0f, edgeSpeed(500f, 1000f), 0f)
    }

    @Test
    fun nearTheTopScrollsUpAndNearTheBottomDown() {
        assertTrue(edgeSpeed(50f, 1000f) < 0f)
        assertTrue(edgeSpeed(950f, 1000f) > 0f)
    }

    @Test
    fun theCloserToTheEdgeTheFaster() {
        assertTrue(edgeSpeed(10f, 1000f) < edgeSpeed(100f, 1000f)) // both negative: -0.93 < -0.33
        assertTrue(edgeSpeed(990f, 1000f) > edgeSpeed(900f, 1000f))
    }

    @Test
    fun beyondTheEdgeIsCappedAtFullSpeed() {
        assertEquals(-1f, edgeSpeed(-200f, 1000f), 0f)
        assertEquals(1f, edgeSpeed(1300f, 1000f), 0f)
    }

    @Test
    fun noViewportMeansNoScroll() {
        assertEquals(0f, edgeSpeed(10f, 0f), 0f)
    }

    @Test
    fun firstLinesUnderTheTopBarDoNotScroll() {
        // viewport 2376 px, top bar bottom at 288, band 77: a finger on the first lines (y 288..365 = band) scrolls
        // gently, one a little lower (y 400) does not, and under the bar itself it is full speed
        assertEquals(0f, edgeSpeed(400f, 2376f, top = 288f, bottom = 2244f, band = 77f), 0f)
        assertTrue(edgeSpeed(300f, 2376f, top = 288f, bottom = 2244f, band = 77f) < 0f)
        assertEquals(-1f, edgeSpeed(100f, 2376f, top = 288f, bottom = 2244f, band = 77f), 0f)
    }

    @Test
    fun bottomBandEndsAtTheNavigationBar() {
        assertEquals(0f, edgeSpeed(2100f, 2376f, top = 288f, bottom = 2244f, band = 77f), 0f)
        assertTrue(edgeSpeed(2200f, 2376f, top = 288f, bottom = 2244f, band = 77f) > 0f)
    }
}
