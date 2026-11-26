package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkChangesTest {
    private val box = MatchRect(0.1f, 0.1f, 0.5f, 0.2f)

    private fun mark(id: String, page: Int = 0, color: AnnotationColor? = null) =
        Annotation(id, page, AnnotationType.HIGHLIGHT, box, null, color)

    private fun mapOf(vararg marks: Annotation) = marks.associateBy { it.id }

    @Test
    fun noChangeGivesNoSteps() {
        val list = mapOf(mark("a"))
        assertEquals(emptyList<MarkChange>(), markChanges(list, list))
    }

    @Test
    fun addedDeletedAndRestyledMarksEachGiveOneStep() {
        val old = mapOf(mark("a"), mark("b"))
        val now = mapOf(mark("a", color = AnnotationColor.RED), mark("c"))
        val steps = markChanges(old, now).orEmpty()
        assertEquals(3, steps.size)
        assertEquals(MarkChange(null, mark("c")), steps.single { it.before == null })
        assertEquals(MarkChange(mark("b"), null), steps.single { it.after == null })
        assertEquals(
            MarkChange(mark("a"), mark("a", color = AnnotationColor.RED)),
            steps.single { it.before != null && it.after != null },
        )
    }

    @Test
    fun aMarkOnAnotherPageIsNotAUserEdit() {
        assertNull(markChanges(mapOf(mark("a", page = 0)), mapOf(mark("a", page = 1))))
    }

    @Test
    fun renamedChangesOnlyTheMatchingId() {
        val step = MarkChange(mark("a"), mark("a", color = AnnotationColor.RED))
        assertEquals("z", step.renamed("a", "z").before?.id)
        assertEquals("z", step.renamed("a", "z").after?.id)
        assertEquals("a", step.renamed("b", "z").before?.id)
    }
}
