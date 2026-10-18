package com.paperly.app.core.file

import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickMarksStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = QuickMarksStore(context)
    private val sum = "a".repeat(64)
    private val marks = QuickMarks(
        annotations = listOf(
            EpubAnnotation("", "{\"href\":\"c1.xhtml\"}", AnnotationType.HIGHLIGHT, AnnotationColor.AMBER, null),
            EpubAnnotation("", "{\"href\":\"c2.xhtml\"}", AnnotationType.NOTE, null, "মন্তব্য"),
        ),
        bookmarks = listOf("{\"href\":\"c3.xhtml\"}"),
        locator = "{\"href\":\"c4.xhtml\"}",
    )

    @Test
    fun marksComeBackForTheSameChecksum() = runBlocking {
        store.save(sum, marks)
        assertEquals(marks, store.load(sum))
    }

    @Test
    fun aBookWithoutMarksOrAPositionGivesNothing() = runBlocking {
        assertNull(store.load("b".repeat(64)))
        val empty = QuickMarks(emptyList(), emptyList(), null)
        assertEquals(empty, decodeQuickMarks(encodeQuickMarks(empty)))
    }

    @Test
    fun anUnsafeChecksumNeverTouchesTheDisk() = runBlocking {
        store.save("../escape", marks)
        assertNull(store.load("../escape"))
        assertFalse(File(context.filesDir, "escape.json").exists())
    }

    @Test
    fun aCorruptOrForeignFileIsIgnored() = runBlocking {
        File(context.filesDir, QUICK_MARKS_DIR).mkdirs()
        File(context.filesDir, "$QUICK_MARKS_DIR/$sum.json").writeText("not json")
        assertNull(store.load(sum))
        assertNull(decodeQuickMarks("{\"v\":2}"))
    }

    @Test
    fun deleteForgetsTheBook() = runBlocking {
        store.save(sum, marks)
        store.delete(sum)
        assertNull(store.load(sum))
        assertTrue(File(context.filesDir, QUICK_MARKS_DIR).listFiles().orEmpty().isEmpty())
    }
}
