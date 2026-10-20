package com.paperly.app.feature.reader

import androidx.compose.ui.unit.IntOffset
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.lookFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupToolStyleTest {
    @Test
    fun menuNeverGoesUnderTheTopBarOrBelowTheBottomLimit() {
        // above would be under the top bar (minY 150): go below the selection instead
        assertEquals(400f, popupY(100f, 400f, 900f, 150f), 0f)
        // nothing fits: stay inside the visible area
        assertEquals(150f, popupY(100f, null, 900f, 150f), 0f)
        assertEquals(900f, popupY(null, 1200f, 900f, 150f), 0f)
    }

    @Test
    fun eachToolKeepsItsOwnLook() {
        val tools = mapOf(
            AnnotationType.HIGHLIGHT to AnnotateStyle(AnnotationType.HIGHLIGHT, AnnotationColor.GREEN),
            AnnotationType.SQUIGGLY to AnnotateStyle(
                AnnotationType.SQUIGGLY,
                AnnotationColor.BLUE,
                AnnotationStroke.THICK,
            ),
        )
        assertEquals(AnnotationColor.GREEN, tools.lookFor(AnnotationType.HIGHLIGHT).color)
        assertEquals(AnnotationStroke.THICK, tools.lookFor(AnnotationType.SQUIGGLY).stroke)
    }

    @Test
    fun aToolNobodyStyledYetGetsItsDefault() {
        val none = emptyMap<AnnotationType, AnnotateStyle>()
        assertEquals(AnnotationColor.AMBER, none.lookFor(AnnotationType.HIGHLIGHT).color)
        assertEquals(AnnotationColor.RED, none.lookFor(AnnotationType.UNDERLINE).color)
    }

    @Test
    fun aNoteHasNoLookSoTheDotFallsBackToHighlight() {
        assertEquals(AnnotationType.HIGHLIGHT, toolInUse(AnnotateStyle(type = AnnotationType.NOTE)))
        assertEquals(AnnotationType.SQUIGGLY, toolInUse(AnnotateStyle(type = AnnotationType.SQUIGGLY)))
    }

    @Test
    fun barKeepsItsSideMarginsAndNeverGoesNegative() {
        assertEquals(12, clampBarX(0f, 300, 400, 12)) // wanted at the edge: 12 in
        assertEquals(88, clampBarX(500f, 300, 400, 12)) // wanted past the right: 12 from the right edge
        assertEquals(12, clampBarX(50f, 500, 400, 12)) // wider than the room: stays at the margin, no crash
    }

    @Test
    fun panelOpensAwayFromTheText() {
        val limits = { y: Int, above: Boolean -> BarPlacement(IntOffset(0, y), above, 150, 1800) }
        // bar above the text: the panel goes above the bar, never over the marked line below
        assertEquals(246, panelY(limits(500, true), 110, 250, 4))
        // bar below the text: the panel goes below the bar
        assertEquals(614, panelY(limits(500, false), 110, 250, 4))
    }

    @Test
    fun panelUsesTheOtherSideOnlyWhenTheAwaySideHasNoRoom() {
        val limits = { y: Int, above: Boolean -> BarPlacement(IntOffset(0, y), above, 150, 1800) }
        // bar above the text but right under the top bar: no room above, so below the bar
        assertEquals(274, panelY(limits(160, true), 110, 250, 4))
        // bar below the text at the very bottom: no room below, so above the bar
        assertEquals(1396, panelY(limits(1650, false), 110, 250, 4))
        // no room anywhere: stays inside the visible area
        assertEquals(150, panelY(limits(160, true), 110, 2000, 4))
    }

    @Test
    fun barIsAboveTheTextOnlyWhenTheFirstLineIsOnScreenWithRoom() {
        assertTrue(popupAbove(300f, 600f, 150f))
        assertFalse(popupAbove(100f, 600f, 150f)) // would be under the top bar
        assertTrue(popupAbove(100f, null, 150f)) // nowhere else to go
        assertFalse(popupAbove(null, 600f, 150f)) // first line scrolled away
    }
}
