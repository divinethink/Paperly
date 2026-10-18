package com.paperly.app.data.reader

import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import com.paperly.app.domain.reader.EpubMarkWriter
import com.paperly.app.domain.reader.LibraryPdfFiles
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

/** Plain JVM test: orchestration only (writer, files, settings are fakes). */
class EpubHighlightFileSyncImplTest {
    private val book = File("book")
    private val mark = EpubAnnotation("a1", "{}", AnnotationType.HIGHLIGHT, AnnotationColor.AMBER, null)

    private class FakeSettings(on: Boolean) : PdfFileWriteSettings {
        private val state = MutableStateFlow(on)
        override val writeHighlights: Flow<Boolean> = state

        override suspend fun setWriteHighlights(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakeFiles(val epub: File?) : LibraryPdfFiles {
        val replaced = mutableListOf<Triple<String, String, Long>>()

        override suspend fun writableFile(documentId: String): File? = null

        override suspend fun writableEpub(documentId: String): File? = epub

        override suspend fun replaced(documentId: String, checksum: String, sizeBytes: Long) {
            replaced += Triple(documentId, checksum, sizeBytes)
        }
    }

    private class FakeWriter(private val result: PdfWriteResult) : EpubMarkWriter {
        val calls = mutableListOf<Pair<List<EpubAnnotation>, Boolean>>()
        var verified: Boolean? = null

        override suspend fun write(
            file: File,
            marks: List<EpubAnnotation>,
            removeStale: Boolean,
            verify: suspend (File) -> Boolean,
        ): PdfWriteResult {
            calls += marks to removeStale
            verified = verify(file)
            return result
        }
    }

    private class FakeAnnotations(private val items: List<EpubAnnotation>) : EpubAnnotationRepository {
        override fun observe(documentId: String): Flow<List<EpubAnnotation>> = flowOf(items)

        override suspend fun add(documentId: String, locatorJson: String, content: AnnotationContent) = Unit

        override suspend fun update(id: String, content: AnnotationContent) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private fun sync(on: Boolean, files: FakeFiles, writer: FakeWriter) = EpubHighlightFileSyncImpl(
        CoroutineScope(Dispatchers.Unconfined),
        files,
        FakeAnnotations(listOf(mark)),
        writer,
        { true },
        FakeSettings(on),
    )

    @Test
    fun settingOff_writesNothing() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "c", 5))
        val files = FakeFiles(book)
        assertNull(sync(false, files, writer).writeNow("d1"))
        assertTrue(writer.calls.isEmpty() && files.replaced.isEmpty())
    }

    @Test
    fun notAWritableLibraryEpub_writesNothing() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "c", 5))
        assertNull(sync(true, FakeFiles(null), writer).writeNow("d1"))
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun written_recordsNewChecksumAndSize_andRemovesStaleMarks() = runBlocking {
        val writer = FakeWriter(PdfWriteResult.Written(1, "newsum", 99))
        val files = FakeFiles(book)
        sync(true, files, writer).writeNow("d1")
        assertEquals(listOf(Triple("d1", "newsum", 99L)), files.replaced)
        assertEquals(listOf(mark) to true, writer.calls.single())
        assertEquals(true, writer.verified)
    }

    @Test
    fun skippedOrNothing_leavesTheRowAlone() = runBlocking {
        for (result in listOf(PdfWriteResult.Skipped(PdfSkipReason.TOO_LARGE), PdfWriteResult.NothingToWrite)) {
            val files = FakeFiles(book)
            assertEquals(result, sync(true, files, FakeWriter(result)).writeNow("d1"))
            assertTrue(files.replaced.isEmpty())
        }
    }
}
