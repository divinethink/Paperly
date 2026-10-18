package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfWriteResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBoxAnnotationReaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val writer by lazy { PdfBoxAnnotationWriter(context) }
    private val reader by lazy { PdfBoxAnnotationReader(context) }
    private val first = MatchRect(0.1f, 0.1f, 0.5f, 0.2f)
    private val second = MatchRect(0.1f, 0.3f, 0.4f, 0.35f)

    @Before
    fun setUp() = PDFBoxResourceLoader.init(context)

    private fun samplePdf(): File {
        val file = folder.newFile("doc.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4).apply { setRotation(90) })
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
        return file
    }

    private fun write(file: File, items: List<PdfAnnotationItem>) = runBlocking { writer.write(file, items) }

    private fun read(file: File) = runBlocking { reader.read(file) }

    private fun assertRect(expected: MatchRect, actual: MatchRect) {
        assertEquals(expected.left, actual.left, DELTA)
        assertEquals(expected.top, actual.top, DELTA)
        assertEquals(expected.right, actual.right, DELTA)
        assertEquals(expected.bottom, actual.bottom, DELTA)
    }

    @Test
    fun readsBackWhatTheWriterWrote() {
        val file = samplePdf()
        val items = listOf(
            PdfAnnotationItem("a1", 0, AnnotationType.HIGHLIGHT, AnnotationColor.BLUE, listOf(first, second), null),
            PdfAnnotationItem(
                "a2",
                1,
                AnnotationType.UNDERLINE,
                AnnotationColor.RED,
                listOf(first),
                "memo",
                AnnotationStroke.THICK,
            ),
        )
        assertTrue(write(file, items) is PdfWriteResult.Written)

        val marks = read(file)

        assertEquals(listOf("file:a1", "file:a2"), marks.map { it.id })
        assertEquals(listOf(0, 1), marks.map { it.page })
        assertEquals(AnnotationType.HIGHLIGHT, marks[0].type)
        assertEquals(AnnotationColor.BLUE, marks[0].color)
        assertEquals(2, marks[0].rects.size)
        assertRect(first, marks[0].rects[0]) // page 0 is rotated by 90: the inverse maths must undo it
        assertRect(second, marks[0].rects[1])
        assertEquals(AnnotationType.UNDERLINE, marks[1].type)
        assertEquals(AnnotationColor.RED, marks[1].color)
        assertEquals(AnnotationStroke.THICK, marks[1].stroke)
        assertEquals("memo", marks[1].noteText)
        assertNull(marks[0].stroke)
    }

    @Test
    fun aFileWithoutMarksOrAMissingFileGivesNothing() {
        assertTrue(read(samplePdf()).isEmpty())
        assertTrue(read(File(folder.root, "missing.pdf")).isEmpty())
    }

    @Test
    fun theSameMarkUnderAnotherIdIsNotWrittenTwice() {
        val file = samplePdf()
        val a = PdfAnnotationItem("a1", 0, AnnotationType.HIGHLIGHT, AnnotationColor.BLUE, listOf(first), null)
        assertTrue(write(file, listOf(a)) is PdfWriteResult.Written)

        val again = write(file, listOf(a.copy(id = "b1")))

        assertTrue(again is PdfWriteResult.NothingToWrite)
        assertEquals(1, read(file).size)
    }

    private companion object {
        const val DELTA = 0.002f
    }
}
