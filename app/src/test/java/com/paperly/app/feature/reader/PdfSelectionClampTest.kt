package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSelectionClampTest {
    private val line = MatchRect(0.1f, 0.5f, 0.9f, 0.52f)

    @Test
    fun wholeLineRectIsTrimmedToTheTouchPoints() {
        val out = clampEnds(listOf(line), Offset(0.4f, 0.51f), Offset(0.5f, 0.511f))
        assertEquals(0.37f, out.single().left, EPS)
        assertEquals(0.53f, out.single().right, EPS)
    }

    @Test
    fun rightToLeftDragGivesTheSameTrim() {
        val out = clampEnds(listOf(line), Offset(0.5f, 0.51f), Offset(0.4f, 0.51f))
        assertEquals(0.37f, out.single().left, EPS)
        assertEquals(0.53f, out.single().right, EPS)
    }

    @Test
    fun engineSnappedRectInsideTheMarginIsUntouched() {
        val snapped = MatchRect(0.39f, 0.5f, 0.52f, 0.52f)
        assertEquals(listOf(snapped), clampEnds(listOf(snapped), Offset(0.4f, 0.51f), Offset(0.5f, 0.51f)))
    }

    @Test
    fun onlyFirstLeftAndLastRightAreTrimmedAcrossLines() {
        val mid = MatchRect(0.1f, 0.52f, 0.9f, 0.54f)
        val last = MatchRect(0.1f, 0.54f, 0.9f, 0.56f)
        val out = clampEnds(listOf(line, mid, last), Offset(0.6f, 0.55f), Offset(0.3f, 0.51f)) // dragged upwards
        assertEquals(0.27f, out[0].left, EPS)
        assertEquals(0.9f, out[0].right, EPS)
        assertEquals(mid, out[1])
        assertEquals(0.63f, out[2].right, EPS)
    }

    @Test
    fun otherCellsOfTheRowBetweenTheEndsAreDropped() {
        val nameLine1 = MatchRect(0.20f, 0.50f, 0.27f, 0.52f)
        val nameLine2 = MatchRect(0.20f, 0.52f, 0.30f, 0.54f)
        val otherCell = MatchRect(0.55f, 0.50f, 0.85f, 0.52f) // next column, same row
        val serial = MatchRect(0.03f, 0.56f, 0.07f, 0.58f) // serial-number column of the next row
        val lastName = MatchRect(0.20f, 0.56f, 0.28f, 0.58f)
        val out = clampEnds(
            listOf(nameLine1, nameLine2, otherCell, serial, lastName),
            Offset(0.21f, 0.51f),
            Offset(0.27f, 0.57f),
        )
        assertEquals(listOf(nameLine1.copy(left = 0.20f), nameLine2, lastName), out)
    }

    @Test
    fun fullWidthMiddleLinesOfAParagraphAreKept() {
        val a = MatchRect(0.5f, 0.50f, 0.9f, 0.52f)
        val b = MatchRect(0.1f, 0.52f, 0.9f, 0.54f)
        val c = MatchRect(0.1f, 0.54f, 0.4f, 0.56f)
        val out = clampEnds(listOf(a, b, c), Offset(0.55f, 0.51f), Offset(0.35f, 0.55f))
        assertEquals(3, out.size)
        assertEquals(b, out[1])
    }

    @Test
    fun aCellTheFingerReachedIsKept() {
        val name = MatchRect(0.20f, 0.50f, 0.27f, 0.52f)
        val nextCell = MatchRect(0.30f, 0.50f, 0.45f, 0.52f)
        val last = MatchRect(0.20f, 0.52f, 0.30f, 0.54f)
        val out = clampEnds(listOf(name, nextCell, last), Offset(0.21f, 0.51f), Offset(0.40f, 0.53f))
        assertEquals(3, out.size)
        assertEquals(nextCell, out[1])
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
