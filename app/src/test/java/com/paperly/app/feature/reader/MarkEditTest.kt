package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MarkEditTest {
    private val eps = 1e-4f
    private val frame = MatchRect(0.1f, 0.2f, 0.5f, 0.4f)
    private val lines = listOf(MatchRect(0.1f, 0.2f, 0.5f, 0.3f), MatchRect(0.1f, 0.3f, 0.3f, 0.4f))

    private fun assertRect(expected: MatchRect, actual: MatchRect) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun sameFrameKeepsEveryPart() {
        remapRects(lines, frame, frame).zip(lines).forEach { (got, want) -> assertRect(want, got) }
    }

    @Test
    fun movingShiftsAllPartsTogether() {
        val moved = remapRects(lines, frame, MatchRect(0.2f, 0.3f, 0.6f, 0.5f))
        assertRect(MatchRect(0.2f, 0.3f, 0.6f, 0.4f), moved[0])
        assertRect(MatchRect(0.2f, 0.4f, 0.4f, 0.5f), moved[1])
    }

    @Test
    fun wideningStretchesPartsInProportion() {
        val wide = remapRects(lines, frame, MatchRect(0.1f, 0.2f, 0.9f, 0.4f))
        assertRect(MatchRect(0.1f, 0.2f, 0.9f, 0.3f), wide[0])
        assertRect(MatchRect(0.1f, 0.3f, 0.5f, 0.4f), wide[1])
    }

    @Test
    fun partsStayInsideThePage() {
        remapRects(lines, frame, MatchRect(0.9f, 0.9f, 1.8f, 1.4f)).forEach {
            assertEquals(true, it.left in 0f..1f && it.right in 0f..1f && it.top in 0f..1f && it.bottom in 0f..1f)
        }
    }

    @Test
    fun previewFollowsTheBoxOnlyForThePickedMark() {
        val picked = Annotation("a", 0, AnnotationType.HIGHLIGHT, frame, null, rects = lines)
        val other = Annotation("b", 0, AnnotationType.UNDERLINE, frame, null, rects = lines)
        val editing = EditingMark("a", 0, lines, frame)
        val shown = previewMarks(listOf(picked, other), editing, MatchRect(0.2f, 0.3f, 0.6f, 0.5f))
        assertRect(MatchRect(0.2f, 0.3f, 0.6f, 0.4f), shown[0].rects[0])
        assertEquals(lines, shown[1].rects)
    }

    @Test
    fun noPickedMarkLeavesTheListUntouched() {
        val items = listOf(Annotation("a", 0, AnnotationType.HIGHLIGHT, frame, null, rects = lines))
        assertSame(items, previewMarks(items, null, null))
    }
}
