package com.paperly.app.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPinchZoomTest {
    @Test
    fun spreadingTheFingersEnlargesTheText() {
        assertEquals(1.5f, pinchedScale(1f, 1.5f), 0.001f)
    }

    @Test
    fun theSizeStartsFromWhatItWasWhenTheFingersWentDown() {
        assertEquals(1.2f, pinchedScale(2f, 0.6f), 0.001f)
    }

    @Test
    fun tinyMovesSnapToFiveHundredths() {
        assertEquals(1f, pinchedScale(1f, 1.02f), 0.001f)
        assertEquals(1.05f, pinchedScale(1f, 1.03f), 0.001f)
    }

    @Test
    fun theSizeStaysInsideTheSettingsRange() {
        assertEquals(2.5f, pinchedScale(1f, 10f), 0.001f)
        assertEquals(0.7f, pinchedScale(1f, 0.1f), 0.001f)
    }
}
