package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PdfPendingHitTest {
    private val page = IntSize(1000, 1000)
    private val box = MatchRect(0.2f, 0.4f, 0.8f, 0.44f) // x 200..800, y 400..440
    private val reach = 24f

    @Test
    fun sideMiddlesGrabASideResizeThatKeepsTheOtherSideFixed() {
        val left = hitPending(Offset(205f, 420f), page, listOf(box), reach)
        val right = hitPending(Offset(795f, 420f), page, listOf(box), reach)
        assertEquals(HitKind.RESIZE_X, left?.kind)
        assertEquals(800f, left?.fixed?.x)
        assertEquals(HitKind.RESIZE_X, right?.kind)
        assertEquals(200f, right?.fixed?.x)
    }

    @Test
    fun cornersAndBadgeStillWinNearThemselves() {
        assertEquals(HitKind.RESIZE, hitPending(Offset(200f, 440f), page, listOf(box), reach)?.kind)
        assertEquals(HitKind.REMOVE, hitPending(Offset(800f, 400f), page, listOf(box), reach)?.kind)
    }

    @Test
    fun insideBodyMovesAndOutsideMissesAndEmptyListMisses() {
        assertEquals(HitKind.MOVE, hitPending(Offset(500f, 420f), page, listOf(box), reach)?.kind)
        assertNull(hitPending(Offset(500f, 700f), page, listOf(box), reach))
        assertNull(hitPending(Offset(500f, 420f), page, emptyList(), reach))
    }

    @Test
    fun dragSideChangesOnlyTheWidthAndWorksInBothDirections() {
        val wider = dragSide(800f, 100f, box, page)
        assertNotNull(wider)
        assertEquals(0.1f, wider!!.left, 1e-5f)
        assertEquals(0.8f, wider.right, 1e-5f)
        assertEquals(box.top, wider.top, 0f)
        assertEquals(box.bottom, wider.bottom, 0f)
        val flipped = dragSide(200f, 100f, box, page)
        assertEquals(0.1f, flipped!!.left, 1e-5f)
        assertEquals(0.2f, flipped.right, 1e-5f)
    }

    @Test
    fun dragSideRefusesAnAlmostZeroWidthAndClampsToThePage() {
        assertNull(dragSide(500f, 503f, box, page))
        assertEquals(1f, dragSide(500f, 2000f, box, page)!!.right, 0f)
    }
}
