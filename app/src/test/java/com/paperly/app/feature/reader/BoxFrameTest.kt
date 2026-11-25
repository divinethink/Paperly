package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxFrameTest {
    @Test
    fun frameHoldsAllLines() {
        val frame = frameOf(listOf(MatchRect(0.2f, 0.1f, 0.8f, 0.15f), MatchRect(0.1f, 0.16f, 0.7f, 0.21f)))
        assertEquals(MatchRect(0.1f, 0.1f, 0.8f, 0.21f), frame)
    }

    @Test
    fun noPartsNoFrame() {
        assertNull(frameOf(emptyList()))
    }

    @Test
    fun boxKeyRoundTripsAndHasThickness() {
        assertEquals(AnnotationType.BOX, AnnotationType.fromKey("box"))
        assertTrue(AnnotationStroke.appliesTo(AnnotationType.BOX))
    }
}
