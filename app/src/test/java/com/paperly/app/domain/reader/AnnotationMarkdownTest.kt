package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationMarkdownTest {
    @Test
    fun groupsByHeadingAndKeepsQuoteAndNote() {
        val md = buildAnnotationMarkdown(
            "Book",
            listOf(
                AnnotationExportEntry("Ch 1", AnnotationType.HIGHLIGHT, "first quote", null),
                AnnotationExportEntry("Ch 2", AnnotationType.NOTE, "second", "my note"),
                AnnotationExportEntry("Ch 1", AnnotationType.UNDERLINE, null, null),
            ),
        )
        val expected = "# Book\n\n## Ch 1\n\n> first quote\n**Highlight**\n\n**Underline**\n\n" +
            "## Ch 2\n\n> second\n**Note** — my note\n"
        assertEquals(expected, md)
    }

    @Test
    fun blankTitleAndHeadingAreHandled() {
        val md = buildAnnotationMarkdown(" ", listOf(AnnotationExportEntry("", AnnotationType.NOTE, null, "x")))
        assertEquals("# Annotations\n\n\n**Note** — x\n", md)
    }
}
