package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PageZoomStateTest {
    private val viewport = 400f
    private val base = 400f

    @Test
    fun pinchKeepsPointUnderFocusFixed() {
        val state = PageZoomState()
        val focus = Offset(100f, 200f)
        val scrollDelta = state.transform(2f, focus, Offset.Zero, viewport, base)
        assertEquals(2f, state.zoom, 1e-6f)
        // Horizontal: content x=100 sits at tx + 100 * zoom, which must equal the focus x.
        assertEquals(focus.x, state.offsetX(viewport, base) + 100f * state.zoom, 1e-4f)
        // Vertical: the list scrolls by 100px so the point under the finger stays at y=200.
        assertEquals(100f, scrollDelta, 1e-4f)
    }

    @Test
    fun zoomIsClampedAndOffsetStaysInsideViewport() {
        val state = PageZoomState()
        state.transform(100f, Offset(400f, 0f), Offset.Zero, viewport, base)
        assertEquals(MAX_ZOOM, state.zoom, 1e-6f)
        state.panX(-10_000f, viewport, base)
        assertEquals(viewport - base * MAX_ZOOM, state.offsetX(viewport, base), 1e-4f)
        state.panX(10_000f, viewport, base)
        assertEquals(0f, state.offsetX(viewport, base), 1e-4f)
    }

    @Test
    fun narrowPageIsCentredAtAnyZoomThatStillFits() {
        assertEquals(50f, clampOffset(-30f, 400f, 300f), 1e-6f)
    }
}
