package com.paperly.app.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSelectionSettleTest {
    private val spot = EpubAnchor(10f, 20f, 300f, 60f)

    @Test
    fun sameRectWithinToleranceIsSettled() {
        assertTrue(sameSpot(spot, EpubAnchor(11f, 21f, 299f, 61f)))
    }

    @Test
    fun movedHandleIsNotSettled() {
        assertFalse(sameSpot(spot, EpubAnchor(10f, 20f, 300f, 100f)))
    }

    @Test
    fun nullsAreHandled() {
        assertTrue(sameSpot(null, null))
        assertFalse(sameSpot(null, spot))
    }
}
