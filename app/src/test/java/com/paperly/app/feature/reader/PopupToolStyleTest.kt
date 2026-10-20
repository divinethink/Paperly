package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.lookFor
import org.junit.Assert.assertEquals
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
}
