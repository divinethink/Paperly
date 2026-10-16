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

    private companion object {
        const val EPS = 0.0001f
    }
}
