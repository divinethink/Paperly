package com.paperly.app.feature.reader

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPopupOriginTest {
    private val menu = IntSize(100, 40)
    private val box = IntSize(400, 800)

    @Test
    fun sitsCentredAboveTheSelection() {
        val at = epubPopupOrigin(EpubAnchor(150f, 300f, 250f, 320f), menu, box, 36f)
        assertEquals(IntOffset(150, 224), at)
    }

    @Test
    fun dropsBelowWhenThereIsNoRoomAbove() {
        val at = epubPopupOrigin(EpubAnchor(150f, 20f, 250f, 40f), menu, box, 36f)
        assertEquals(IntOffset(150, 76), at)
    }

    @Test
    fun staysInsideTheBoxAtTheEdges() {
        val at = epubPopupOrigin(EpubAnchor(0f, 300f, 20f, 320f), menu, box, 36f)
        assertEquals(IntOffset(0, 224), at)
    }

    @Test
    fun withoutAnAnchorItGoesTopCentre() {
        assertEquals(IntOffset(150, 0), epubPopupOrigin(null, menu, box, 36f))
    }
}
