package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Library copy: edits (move, resize, new colour) and deletions made in the app reach the PDF file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBoxMirrorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val writer by lazy { PdfBoxAnnotationWriter(context) }
    private val idA = "11111111-1111-4111-8111-111111111111"
    private val idB = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() = PDFBoxResourceLoader.init(context)

    private fun samplePdf(): File = folder.newFile("doc.pdf").also { file ->
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
    }

    private fun mark(id: String, rect: MatchRect, color: AnnotationColor = AnnotationColor.BLUE) =
        PdfAnnotationItem(id, 0, AnnotationType.HIGHLIGHT, color, listOf(rect), null)

    private fun mirror(file: File, items: List<PdfAnnotationItem>, removed: Set<String> = emptySet()) =
        runBlocking { writer.mirror(file, items, removed) { true } }

    private fun names(file: File): List<String> = PDDocument.load(file).use { doc ->
        doc.getPage(0).getAnnotations().map { it.getAnnotationName() }
    }

    private fun quadMinX(file: File): Float = PDDocument.load(file).use { doc ->
        doc.getPage(0).getAnnotations().single().getRectangle().lowerLeftX
    }

    @Test
    fun movedMarkIsReplacedNotDuplicated() {
        val file = samplePdf()
        val before = MatchRect(0.1f, 0.1f, 0.5f, 0.15f)
        assertTrue(mirror(file, listOf(mark(idA, before))) is PdfWriteResult.Written)
        val oldX = quadMinX(file)

        val moved = MatchRect(0.3f, 0.1f, 0.7f, 0.15f)
        assertTrue(mirror(file, listOf(mark(idA, moved))) is PdfWriteResult.Written)

        assertEquals(listOf(idA), names(file))
        assertTrue(quadMinX(file) > oldX + 50f) // 0.2 of an A4 page is ~119 pt
    }

    @Test
    fun unchangedMarkLeavesTheFileAlone() {
        val file = samplePdf()
        val items = listOf(mark(idA, MatchRect(0.1f, 0.1f, 0.5f, 0.15f)))
        mirror(file, items)
        assertEquals(PdfWriteResult.NothingToWrite, mirror(file, items))
    }

    @Test
    fun recolouredMarkIsReplaced() {
        val file = samplePdf()
        val rect = MatchRect(0.1f, 0.1f, 0.5f, 0.15f)
        mirror(file, listOf(mark(idA, rect, AnnotationColor.BLUE)))
        assertTrue(mirror(file, listOf(mark(idA, rect, AnnotationColor.RED))) is PdfWriteResult.Written)
        assertEquals(listOf(idA), names(file))
    }

    @Test
    fun deletedMarkIsTakenOutAndOthersStay() {
        val file = samplePdf()
        val a = mark(idA, MatchRect(0.1f, 0.1f, 0.5f, 0.15f))
        val b = mark(idB, MatchRect(0.1f, 0.3f, 0.5f, 0.35f))
        mirror(file, listOf(a, b))

        assertTrue(mirror(file, listOf(a), setOf(idB)) is PdfWriteResult.Written)
        assertEquals(listOf(idA), names(file))
    }

    @Test
    fun lastMarkCanBeDeletedAndUnknownMarksAreNeverTouched() {
        val file = samplePdf()
        val a = mark(idA, MatchRect(0.1f, 0.1f, 0.5f, 0.15f))
        val b = mark(idB, MatchRect(0.1f, 0.3f, 0.5f, 0.35f))
        mirror(file, listOf(a, b))

        // idB is not in the app's list and not in `removed`: it was never ours to delete, so it stays
        assertEquals(PdfWriteResult.NothingToWrite, mirror(file, listOf(a)))
        assertEquals(listOf(idA, idB), names(file))

        assertTrue(mirror(file, emptyList(), setOf(idA, idB)) is PdfWriteResult.Written)
        assertEquals(emptyList<String>(), names(file))
    }
}
