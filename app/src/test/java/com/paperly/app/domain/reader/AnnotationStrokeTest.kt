package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationStrokeTest {
    @Test
    fun keysRoundTripAndUnknownIsNull() {
        AnnotationStroke.entries.forEach { assertEquals(it, AnnotationStroke.fromKey(it.key)) }
        assertNull(AnnotationStroke.fromKey(null))
        assertNull(AnnotationStroke.fromKey("huge"))
    }

    @Test
    fun thicknessGrowsFromThinToThick() {
        assertTrue(AnnotationStroke.THIN.dp < AnnotationStroke.MEDIUM.dp)
        assertTrue(AnnotationStroke.MEDIUM.dp < AnnotationStroke.THICK.dp)
    }

    @Test
    fun onlyLineTypesHaveAThickness() {
        assertTrue(AnnotationStroke.appliesTo(AnnotationType.UNDERLINE))
        assertTrue(AnnotationStroke.appliesTo(AnnotationType.SQUIGGLY))
        assertTrue(AnnotationStroke.appliesTo(AnnotationType.STRIKETHROUGH))
        assertFalse(AnnotationStroke.appliesTo(AnnotationType.HIGHLIGHT))
        assertFalse(AnnotationStroke.appliesTo(AnnotationType.NOTE))
    }
}
