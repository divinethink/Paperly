package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageLocatorTest {
    // page 4 starts 300px above the viewport top and is 1000 tall; page 5 follows; page 6 starts at 1700
    private val boxes = listOf(PageBox(4, -300f, 1000f), PageBox(5, 700f, 1000f), PageBox(6, 1700f, 1000f))

    @Test
    fun fingerInsideTheStartPageStaysThere() {
        val hit = locateInPages(boxes, from = 5, y = 400f, x = 0.25f)!!
        assertEquals(5, hit.page)
        assertEquals(0.4f, hit.point.y, EPS)
        assertEquals(0.25f, hit.point.x, EPS)
    }

    @Test
    fun fingerBelowTheStartPageLandsOnTheNextOne() {
        val hit = locateInPages(boxes, from = 5, y = 1250f, x = 0.5f)!!
        assertEquals(6, hit.page)
        assertEquals(0.25f, hit.point.y, EPS)
    }

    @Test
    fun fingerAboveTheStartPageLandsOnThePreviousOne() {
        val hit = locateInPages(boxes, from = 5, y = -100f, x = 0.5f)!!
        assertEquals(4, hit.page)
        assertEquals(0.9f, hit.point.y, EPS)
    }

    @Test
    fun beyondEveryShownPageClampsToTheEdgeOfTheNearestOne() {
        val below = locateInPages(boxes, from = 4, y = 9000f, x = 2f)!!
        assertEquals(6, below.page)
        assertEquals(1f, below.point.y, EPS)
        assertEquals(1f, below.point.x, EPS)
        val above = locateInPages(boxes, from = 6, y = -9000f, x = -1f)!!
        assertEquals(4, above.page)
        assertEquals(0f, above.point.y, EPS)
        assertEquals(0f, above.point.x, EPS)
    }

    @Test
    fun startPageNoLongerShownGivesNull() {
        assertNull(locateInPages(boxes, from = 9, y = 10f, x = 0.5f))
        assertNull(viewportYOf(boxes, 9, 10f))
    }

    @Test
    fun viewportYAddsThePageOffset() {
        assertEquals(800f, viewportYOf(boxes, 5, 100f)!!, EPS)
        assertEquals(-250f, viewportYOf(boxes, 4, 50f)!!, EPS)
    }

    @Test
    fun forwardAcrossPagesFollowsPageNumberThenReadingOrder() {
        val a = PagePoint(2, Offset(0.9f, 0.9f))
        val b = PagePoint(3, Offset(0.1f, 0.1f))
        assertTrue(isForwardAcross(a, b, 0.02f))
        assertFalse(isForwardAcross(b, a, 0.02f))
        assertTrue(isForwardAcross(PagePoint(3, Offset(0.2f, 0.5f)), PagePoint(3, Offset(0.6f, 0.5f)), 0.02f))
    }

    @Test
    fun trimStartMovesOnlyTheFirstLineLeftEdgeWhenTheTouchIsOnIt() {
        val line1 = MatchRect(0.1f, 0.50f, 0.9f, 0.52f)
        val line2 = MatchRect(0.1f, 0.52f, 0.9f, 0.54f)
        val out = trimStart(listOf(line1, line2), Offset(0.5f, 0.51f))
        assertEquals(0.47f, out[0].left, EPS)
        assertEquals(line2, out[1])
    }

    @Test
    fun trimStartLeavesTheEngineSnapWhenTheTouchIsOffTheLine() {
        val line = MatchRect(0.1f, 0.50f, 0.9f, 0.52f)
        assertEquals(listOf(line), trimStart(listOf(line), Offset(0.5f, 0.80f)))
    }

    @Test
    fun trimEndMovesOnlyTheLastLineRightEdge() {
        val line1 = MatchRect(0.1f, 0.50f, 0.9f, 0.52f)
        val line2 = MatchRect(0.1f, 0.52f, 0.9f, 0.54f)
        val out = trimEnd(listOf(line1, line2), Offset(0.4f, 0.53f))
        assertEquals(line1, out[0])
        assertEquals(0.43f, out[1].right, EPS)
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
