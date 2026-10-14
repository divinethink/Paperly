package com.paperly.app.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfQuickBoxTest {
    private val eps = 1e-5f

    @Test
    fun pressWithoutDragIsAOneLineBoxCentredOnTheFinger() {
        val r = quickBoxRect(0.5f, 0.5f, null)
        assertEquals(0.5f, (r.left + r.right) / 2, 1e-5f)
        assertEquals(0.5f, (r.top + r.bottom) / 2, 1e-5f)
        assertTrue(r.right - r.left > r.bottom - r.top)
    }

    @Test
    fun boxStaysInsideThePageAtEveryEdge() {
        listOf(0f to 0f, 1f to 1f, 0f to 1f, 1f to 0f).forEach { (x, y) ->
            val r = quickBoxRect(x, y, null)
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1f + eps && r.bottom <= 1f + eps)
            assertTrue(r.right > r.left && r.bottom > r.top)
        }
    }

    @Test
    fun dragRunsFromThePressPointToTheFingerInEitherDirection() {
        val forward = quickBoxRect(0.2f, 0.4f, 0.7f)
        val backward = quickBoxRect(0.7f, 0.4f, 0.2f)
        assertEquals(forward, backward)
        assertEquals(0.2f, forward.left, 1e-5f)
        assertEquals(0.7f, forward.right, 1e-5f)
    }

    @Test
    fun aTinyOrEdgeDragKeepsAUsableWidthInsideThePage() {
        val tiny = quickBoxRect(0.5f, 0.5f, 0.501f)
        assertTrue(tiny.right - tiny.left >= 0.05f - 1e-5f)
        val edge = quickBoxRect(1f, 0.5f, 1f)
        assertTrue(edge.right <= 1f && edge.right - edge.left >= 0.05f - 1e-5f)
    }
}
