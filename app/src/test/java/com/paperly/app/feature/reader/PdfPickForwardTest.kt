package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPickForwardTest {
    private val line = MatchRect(0.1f, 0.5f, 0.9f, 0.52f)

    @Test
    fun fingerDriftingUpAlongTheLineKeepsTheWholeSelection() {
        // device log: the handle sits below the text, so the finger rises while it moves right
        val out = clampEnds(listOf(line), Offset(0.12f, 0.51f), Offset(0.6f, 0.47f))
        assertEquals(0.1f, out.single().left, EPS)
        assertEquals(0.63f, out.single().right, EPS)
    }

    @Test
    fun oneLineIsDecidedByLeftRightOnly() {
        assertEquals(true, pickForward(listOf(line), Offset(0.2f, 0.51f), Offset(0.6f, 0.40f)))
        assertEquals(false, pickForward(listOf(line), Offset(0.6f, 0.51f), Offset(0.2f, 0.60f)))
    }

    @Test
    fun severalLinesFollowReadingOrder() {
        val next = MatchRect(0.1f, 0.54f, 0.5f, 0.56f)
        assertEquals(true, pickForward(listOf(line, next), Offset(0.8f, 0.51f), Offset(0.3f, 0.55f)))
        assertEquals(false, pickForward(listOf(line, next), Offset(0.3f, 0.55f), Offset(0.8f, 0.51f)))
    }

    @Test
    fun pinOnlyAcrossPagesInOrderOrOnTheSamePage() {
        assertEquals(true, pinnable(PagePoint(1, Offset.Zero), PagePoint(1, Offset(0.5f, 0.1f))))
        assertEquals(true, pinnable(PagePoint(1, Offset.Zero), PagePoint(2, Offset.Zero)))
        assertEquals(false, pinnable(PagePoint(2, Offset.Zero), PagePoint(1, Offset.Zero)))
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
