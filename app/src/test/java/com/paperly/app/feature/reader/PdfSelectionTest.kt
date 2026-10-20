package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSelectionTest {
    private val a = MatchRect(0.1f, 0.5f, 0.9f, 0.52f)
    private val b = MatchRect(0.1f, 0.1f, 0.6f, 0.12f)

    @Test
    fun singleKeepsPageRectsAndText() {
        val sel = PdfSelection.single(3, listOf(a), "alpha")
        assertEquals("alpha", sel.text)
        assertEquals(listOf(a), sel.rectsOn(3))
        assertTrue(sel.markIds.isEmpty())
    }

    @Test
    fun partsJoinTheirTextInReadingOrder() {
        val sel = PdfSelection(
            listOf(SelectionPart(2, listOf(a), "end of p2"), SelectionPart(3, listOf(b), "start of p3")),
        )
        assertEquals("end of p2\nstart of p3", sel.text)
    }

    @Test
    fun rectsOnIsEmptyForAPageWithoutAPart() {
        assertTrue(PdfSelection.single(3, listOf(a), "x").rectsOn(4).isEmpty())
    }

    @Test
    fun onlyTheFirstAndLastPageCarryHandles() {
        val sel = PdfSelection(
            listOf(SelectionPart(2, listOf(a), ""), SelectionPart(3, listOf(a), ""), SelectionPart(4, listOf(b), "")),
        )
        assertEquals(listOf(true, false, false), listOf(2, 3, 4).map { sel.startsOn(it) })
        assertEquals(listOf(false, false, true), listOf(2, 3, 4).map { sel.endsOn(it) })
    }

    @Test
    fun aOnePageSelectionStartsAndEndsOnItsPage() {
        val sel = PdfSelection.single(5, listOf(a), "x")
        assertTrue(sel.startsOn(5) && sel.endsOn(5))
    }

    @Test
    fun popupPrefersAboveThenBelowThenParks() {
        assertEquals(40f, popupY(40f, 300f, 800f), 0f)
        assertEquals(300f, popupY(-5f, 300f, 800f), 0f)
        assertEquals(0f, popupY(null, null, 800f), 0f)
        assertEquals(300f, popupY(null, 300f, 800f), 0f) // first page scrolled away: use the last line
        assertEquals(40f, popupY(40f, null, 800f), 0f) // last page scrolled away: use the first line
        assertEquals(800f, popupY(900f, 950f, 800f), 0f)
    }

    @Test
    fun barGoesBelowWhenThePanelWouldNotFitAbove() {
        // bar would fit above the line (y 400 >= top 288) but the 340 px panel above it would not: bar below from the start
        assertTrue(popupAbove(400f, 900f, 288f))
        assertTrue(!popupAbove(400f, 900f, 288f, reserve = 340f))
        assertTrue(popupAbove(700f, 900f, 288f, reserve = 340f)) // room for bar and panel: stays above
        assertTrue(popupAbove(100f, null, 288f, reserve = 340f)) // last line scrolled away: nothing below to use
    }
}
