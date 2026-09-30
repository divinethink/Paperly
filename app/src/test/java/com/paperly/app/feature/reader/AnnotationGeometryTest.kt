package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationGeometryTest {
    private val page = IntSize(1000, 2000)

    @Test
    fun dragInAnyDirectionGivesTheSameNormalisedRect() {
        val forward = dragRect(Offset(100f, 200f), Offset(500f, 600f), page)
        val backward = dragRect(Offset(500f, 600f), Offset(100f, 200f), page)
        assertNotNull(forward)
        assertEquals(forward, backward)
        assertEquals(0.1f, forward!!.left, 1e-6f)
        assertEquals(0.1f, forward.top, 1e-6f)
        assertEquals(0.5f, forward.right, 1e-6f)
        assertEquals(0.3f, forward.bottom, 1e-6f)
    }

    @Test
    fun dragOutsideThePageIsClamped() {
        val rect = dragRect(Offset(-50f, -50f), Offset(5000f, 5000f), page)!!
        assertEquals(0f, rect.left, 0f)
        assertEquals(1f, rect.right, 0f)
        assertEquals(1f, rect.bottom, 0f)
    }

    @Test
    fun accidentalOrInvalidDragsAreIgnored() {
        assertNull(dragRect(Offset(100f, 100f), Offset(102f, 500f), page)) // sliver: too narrow
        assertNull(dragRect(Offset(100f, 100f), Offset(100f, 100f), page)) // no movement
        assertNull(dragRect(Offset(0f, 0f), Offset(10f, 10f), IntSize(0, 0))) // page not measured yet
    }
}
