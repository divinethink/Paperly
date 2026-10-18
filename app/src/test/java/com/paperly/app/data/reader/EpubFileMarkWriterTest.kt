package com.paperly.app.data.reader

import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubXhtmlMarks
import com.paperly.app.domain.reader.PdfWriteResult
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubFileMarkWriterTest {
    private val context = RuntimeEnvironment.getApplication()
    private val writer = EpubFileMarkWriter(context)
    private val chapter = "<html><head><title>T</title></head>" +
        "<body><p>আমি বাংলায় গান গাই, you are here.</p></body></html>"

    private fun epub(): File {
        val file = File(context.cacheDir, "book-${System.nanoTime()}.epub")
        ZipOutputStream(file.outputStream()).use { out ->
            val mime = "application/epub+zip".toByteArray()
            val stored = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            }
            out.putNextEntry(stored)
            out.write(mime)
            out.closeEntry()
            out.putNextEntry(ZipEntry("OEBPS/c1.xhtml"))
            out.write(chapter.toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("OEBPS/data.bin"))
            out.write(ByteArray(300) { it.toByte() })
            out.closeEntry()
        }
        return file
    }

    private fun mark(id: String, text: String, type: AnnotationType = AnnotationType.HIGHLIGHT) = EpubAnnotation(
        id = id,
        locatorJson = "{\"href\":\"OEBPS/c1.xhtml\",\"type\":\"application/xhtml+xml\"," +
            "\"text\":{\"highlight\":\"$text\"}}",
        type = type,
        color = AnnotationColor.GREEN,
        noteText = null,
    )

    private fun chapterOf(file: File): String =
        ZipFile(file).use { z -> z.getInputStream(z.getEntry("OEBPS/c1.xhtml")).readBytes().toString(Charsets.UTF_8) }

    @Test
    fun marksGoIntoTheFileAndNothingElseChanges() = runBlocking {
        val file = epub()
        val result = writer.write(file, listOf(mark("m1", "you are here")), removeStale = true)
        assertTrue(result is PdfWriteResult.Written)
        assertTrue(chapterOf(file).contains("data-paperly-id=\"m1\""))
        assertEquals(chapter, EpubXhtmlMarks.strip(chapterOf(file)))
        ZipFile(file).use { z ->
            assertEquals("mimetype", z.entries().nextElement().name)
            assertEquals(ZipEntry.STORED, z.getEntry("mimetype").method)
            assertEquals(300L, z.getEntry("OEBPS/data.bin").size)
        }
    }

    @Test
    fun banglaTextIsMarked() = runBlocking {
        val file = epub()
        writer.write(file, listOf(mark("b1", "বাংলায় গান")), removeStale = true)
        assertTrue(chapterOf(file).contains(">বাংলায় গান</span>"))
    }

    @Test
    fun writingTwiceIsANoOp() = runBlocking {
        val file = epub()
        writer.write(file, listOf(mark("m1", "you are here")), removeStale = true)
        val second = writer.write(file, listOf(mark("m1", "you are here")), removeStale = true)
        assertEquals(PdfWriteResult.NothingToWrite, second)
    }

    @Test
    fun aDeletedMarkLeavesTheFileWhenStaleRemovalIsOn() = runBlocking {
        val file = epub()
        writer.write(file, listOf(mark("m1", "you are here")), removeStale = true)
        val result = writer.write(file, emptyList(), removeStale = true)
        assertTrue(result is PdfWriteResult.Written)
        assertEquals(chapter, chapterOf(file))
    }

    @Test
    fun otherMarksStayWhenStaleRemovalIsOff() = runBlocking {
        val file = epub()
        writer.write(file, listOf(mark("m1", "you are here")), removeStale = true)
        writer.write(file, listOf(mark("m2", "আমি")), removeStale = false)
        assertEquals(setOf("m1", "m2"), EpubXhtmlMarks.idsIn(chapterOf(file)))
    }

    @Test
    fun aFailedVerifyLeavesTheOriginalUntouched() = runBlocking {
        val file = epub()
        val before = file.readBytes()
        val result = writer.write(file, listOf(mark("m1", "you are here")), removeStale = true) { false }
        assertTrue(result is PdfWriteResult.Failed)
        assertTrue(before.contentEquals(file.readBytes()))
        assertFalse(File(file.parentFile, "${file.name}.epubmarks.tmp").exists())
    }

    @Test
    fun aTextThatIsNotInTheBookWritesNothing() = runBlocking {
        val file = epub()
        assertEquals(PdfWriteResult.NothingToWrite, writer.write(file, listOf(mark("x", "absent text")), true))
    }
}
