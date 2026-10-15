package com.paperly.app.data.reader

import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfSkipReason
import com.paperly.app.domain.reader.PdfWriteResult
import com.paperly.app.domain.settings.PdfFileWriteSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test: the orchestration has no Android dependency (writer, files, settings are fakes). */
class PdfHighlightFileSyncImplTest {
    private val rect = MatchRect(0.1f, 0.1f, 0.5f, 0.2f)
    private val file = File("doc.pdf")

    private class FakeSettings(on: Boolean) : PdfFileWriteSettings {
        private val state = MutableStateFlow(on)
        override val writeHighlights: Flow<Boolean> = state

        override suspend fun setWriteHighlights(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakeFiles(val file: File?) : LibraryPdfFiles {
        val replaced = mutableListOf<Triple<String, String, Long>>()

        override suspend fun writableFile(documentId: String): File? = file

        override suspend fun replaced(documentId: String, checksum: String, sizeBytes: Long) {
            replaced += Triple(documentId, checksum, sizeBytes)
        }
    }

    private class FakeWriter(private val result: PdfWriteResult) : PdfAnnotationWriter {
        val calls = mutableListOf<List<PdfAnnotationItem>>()
        var verified: Boolean? = null

        override suspend fun write(
            file: File,
            items: List<PdfAnnotationItem>,
            verify: suspend (File) -> Boolean,
        ): PdfWriteResult {
            calls += items
            verified = verify(file)
            return result
        }
    }

    private class FakeAnnotations(private val items: List<Annotation>) : AnnotationRepository {
        override fun observe(documentId: String): Flow<List<Annotation>> = flowOf(items)

        override suspend fun add(
            documentId: String,
            page: Int,
            rect: MatchRect,
            content: AnnotationContent,
            rects: List<MatchRect>,
        ) = Unit

        override suspend fun update(id: String, content: AnnotationContent) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private fun sync(on: Boolean, files: FakeFiles, writer: FakeWriter) = PdfHighlightFileSyncImpl(
        CoroutineScope(Dispatchers.Unconfined),
        files,
        FakeAnnotations(listOf(Annotation("a1", 2, AnnotationType.HIGHLIGHT, rect, null))),
        writer,
        { true },
        FakeSettings(on),
    )

    @Test
    fun settingOff_writesNothing() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "c", 5))
        val files = FakeFiles(file)
        assertNull(sync(false, files, writer).writeNow("d1"))
        assertTrue(writer.calls.isEmpty() && files.replaced.isEmpty())
    }

    @Test
    fun notAWritableLibraryPdf_writesNothing() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "c", 5))
        assertNull(sync(true, FakeFiles(null), writer).writeNow("d1"))
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun written_recordsNewChecksumAndSize_andPassesItems() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "newsum", 99))
        val files = FakeFiles(file)
        sync(true, files, writer).writeNow("d1")
        assertEquals(listOf(Triple("d1", "newsum", 99L)), files.replaced)
        val item = writer.calls.single().single()
        assertEquals("a1", item.id)
        assertEquals(2, item.page)
        assertEquals(listOf(rect), item.rects)
        assertEquals(true, writer.verified)
    }

    @Test
    fun skippedOrNothing_leavesTheRowAlone() = runBlocking {
        for (result in listOf(PdfWriteResult.Skipped(PdfSkipReason.TOO_LARGE), PdfWriteResult.NothingToWrite)) {
            val files = FakeFiles(file)
            assertEquals(result, sync(true, files, FakeWriter(result)).writeNow("d1"))
            assertTrue(files.replaced.isEmpty())
        }
    }
}
