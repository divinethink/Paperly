package com.paperly.app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubXhtmlMarksTest {
    private val page = "<?xml version=\"1.0\"?>\n<html><head><title>দুটি কথা</title></head><body>\n" +
        "<p>Hello <b>big</b> world &amp; more.</p>\n<p>Second   paragraph here.</p></body></html>"

    private fun mark(id: String, text: String, before: String = "", after: String = "") =
        XhtmlMark(id, before, text, after, "background-color:#fb0", null)

    @Test
    fun marksTextInsideOneParagraph() {
        val out = EpubXhtmlMarks.apply(page, listOf(mark("a1", "Second paragraph")))
        assertTrue(out.contains("<span class=\"paperly-mark\" data-paperly-id=\"a1\""))
        assertTrue(out.contains(">Second   paragraph</span> here."))
    }

    @Test
    fun marksTextAcrossTagsAndEntities() {
        val out = EpubXhtmlMarks.apply(page, listOf(mark("a2", "Hello big world & more")))
        assertEquals(3, "data-paperly-id=\"a2\"".toRegex().findAll(out).count())
        assertEquals(page, EpubXhtmlMarks.strip(out))
    }

    @Test
    fun textInTheHeadIsNeverMarked() {
        assertEquals(page, EpubXhtmlMarks.apply(page, listOf(mark("a3", "দুটি কথা"))))
    }

    @Test
    fun anAmbiguousTextNeedsItsContext() {
        val twice = "<body><p>one cat</p><p>two cat</p></body>"
        val picked = EpubXhtmlMarks.apply(twice, listOf(mark("c3", "cat", before = "two")))
        assertTrue(picked.contains("<p>two <span"))
        assertFalse(picked.contains("one <span"))
        assertEquals(twice, EpubXhtmlMarks.apply(twice, listOf(mark("c4", "cat"))))
    }

    @Test
    fun applyingTwiceChangesNothing() {
        val once = EpubXhtmlMarks.apply(page, listOf(mark("a5", "Second paragraph")))
        assertEquals(once, EpubXhtmlMarks.apply(once, listOf(mark("a5", "Second paragraph"))))
    }

    @Test
    fun removeTakesOnlyThatMarkAway() {
        val both = EpubXhtmlMarks.apply(page, listOf(mark("x1", "Hello big"), mark("x2", "Second paragraph")))
        val left = EpubXhtmlMarks.remove(both, setOf("x1"))
        assertEquals(setOf("x2"), EpubXhtmlMarks.idsIn(left))
        assertEquals(page, EpubXhtmlMarks.strip(both))
    }

    @Test
    fun aMarkInsideAMarkStillStripsCleanly() {
        val outer = EpubXhtmlMarks.apply(page, listOf(mark("o", "Second paragraph here")))
        val both = EpubXhtmlMarks.apply(outer, listOf(mark("i", "paragraph")))
        assertEquals(setOf("o", "i"), EpubXhtmlMarks.idsIn(both))
        assertEquals(outer, EpubXhtmlMarks.remove(both, setOf("i")))
        assertEquals(page, EpubXhtmlMarks.strip(both))
    }

    @Test
    fun titleAndStyleAreEscaped() {
        val m = XhtmlMark("n1", "", "Second", "", "color:red", "a \"q\" <b> & c")
        val out = EpubXhtmlMarks.apply(page, listOf(m))
        assertTrue(out.contains("title=\"a &quot;q&quot; &lt;b&gt; &amp; c\""))
    }

    @Test
    fun anUnsafeIdIsRefused() {
        assertEquals(page, EpubXhtmlMarks.apply(page, listOf(mark("bad\"id", "Second"))))
    }
}
