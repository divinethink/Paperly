package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfSkipReason
import com.paperly.app.domain.reader.PdfWriteResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class PdfBoxAnnotationWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val writer by lazy { PdfBoxAnnotationWriter(context) }
    private val rect = MatchRect(0.1f, 0.1f, 0.5f, 0.2f)

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

    private fun item(id: String, type: AnnotationType, page: Int = 0, note: String? = null) =
        PdfAnnotationItem(id, page, type, AnnotationColor.BLUE, listOf(rect, MatchRect(0.1f, 0.3f, 0.4f, 0.35f)), note)

    private fun run(file: File, items: List<PdfAnnotationItem>, verify: suspend (File) -> Boolean = { true }) =
        runBlocking { writer.write(file, items, verify) }

    @Test
    fun writesAllTypesAndIsIdempotent() {
        val file = samplePdf()
        val items = listOf(
            item("a1", AnnotationType.HIGHLIGHT),
            item("a2", AnnotationType.UNDERLINE),
            item("a3", AnnotationType.STRIKETHROUGH, page = 1),
            item("a4", AnnotationType.NOTE, note = "hello"),
        )
        val first = run(file, items)
        assertTrue(first is PdfWriteResult.Written)
        assertEquals(4, (first as PdfWriteResult.Written).added)
        assertEquals(file.length(), first.sizeBytes)

        PDDocument.load(file).use { doc ->
            assertEquals(2, doc.numberOfPages)
            val page0 = doc.getPage(0).getAnnotations()
            assertEquals(listOf("a1", "a2", "a4"), page0.map { it.getAnnotationName() })
            assertEquals("Highlight", page0[0].getSubtype())
            assertEquals(16, (page0[0] as PDAnnotationTextMarkup).getQuadPoints().size) // 2 parts x 8
            assertEquals("hello", page0[2].getContents())
            assertEquals("StrikeOut", doc.getPage(1).getAnnotations().single().getSubtype())
        }
        assertEquals(PdfWriteResult.NothingToWrite, run(file, items))
        assertNotNull(File(context.filesDir, "pdf_backups/doc.pdf.bak").takeIf { it.isFile })
    }

    @Test
    fun squigglyIsWrittenAsSquigglySubtype() {
        val file = samplePdf()
        assertTrue(run(file, listOf(item("s1", AnnotationType.SQUIGGLY))) is PdfWriteResult.Written)
        PDDocument.load(file).use { doc ->
            assertEquals("Squiggly", doc.getPage(0).getAnnotations().single().getSubtype())
        }
    }

    @Test
    fun failedVerificationLeavesOriginalAndNoTempBehind() {
        val file = samplePdf()
        val before = file.readBytes()
        val result = run(file, listOf(item("a1", AnnotationType.HIGHLIGHT))) { false }
        assertTrue(result is PdfWriteResult.Failed)
        assertTrue(before.contentEquals(file.readBytes()))
        assertFalse(File(file.parentFile, "doc.pdf.annot.tmp").exists())
    }

    @Test
    fun invalidPageIsSkippedNotCrashed() {
        val file = samplePdf()
        assertEquals(PdfWriteResult.NothingToWrite, run(file, listOf(item("a1", AnnotationType.HIGHLIGHT, page = 9))))
    }

    @Test
    fun tooLargeAndMissingFilesAreSkipped() {
        val big = folder.newFile("big.pdf")
        RandomAccessFile(big, "rw").use { it.setLength(PdfBoxAnnotationWriter.MAX_BYTES + 1) }
        assertEquals(
            PdfWriteResult.Skipped(PdfSkipReason.TOO_LARGE),
            run(big, listOf(item("a1", AnnotationType.HIGHLIGHT))),
        )
        assertEquals(
            PdfWriteResult.Skipped(PdfSkipReason.NOT_FOUND),
            run(File(folder.root, "none.pdf"), listOf(item("a1", AnnotationType.HIGHLIGHT))),
        )
    }

    @Test
    fun notAPdfFailsWithoutTouchingTheFile() {
        val file = folder.newFile("fake.pdf").apply { writeText("not a pdf") }
        assertTrue(run(file, listOf(item("a1", AnnotationType.HIGHLIGHT))) is PdfWriteResult.Failed)
        assertEquals("not a pdf", file.readText())
    }
}
