package com.paperly.app.data.reader

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OriginalFileWriterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val writer = OriginalFileWriter(context)

    private fun sha(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun files(original: String, updated: String): Pair<File, File> {
        val dir = context.cacheDir
        return File(dir, "orig.pdf").apply { writeText(original) } to File(dir, "new.pdf").apply { writeText(updated) }
    }

    @Test
    fun replacesAndTruncatesWhenUnchanged() = runBlocking {
        val (orig, new) = files("long original content", "short")
        val r = writer.overwrite(Uri.fromFile(orig), sha(orig.readBytes()), new, sha(new.readBytes()), "t1")
        assertEquals(OriginalWriteResult.WRITTEN, r)
        assertEquals("short", orig.readText())
    }

    @Test
    fun changedOriginalIsLeftUntouched() = runBlocking {
        val (orig, new) = files("someone else edited this", "new")
        val r = writer.overwrite(Uri.fromFile(orig), sha("old".toByteArray()), new, sha(new.readBytes()), "t2")
        assertEquals(OriginalWriteResult.CHANGED, r)
        assertEquals("someone else edited this", orig.readText())
    }

    @Test
    fun missingOriginalMeansNoPermission() = runBlocking {
        val (_, new) = files("x", "y")
        val gone = Uri.fromFile(File(context.cacheDir, "absent/none.pdf"))
        assertEquals(OriginalWriteResult.NO_PERMISSION, writer.overwrite(gone, "a", new, "b", "t3"))
    }

    @Test
    fun epubBackupKeepsItsExtension() = runBlocking {
        val dir = context.cacheDir
        val orig = File(dir, "orig.epub").apply { writeText("book original") }
        val new = File(dir, "new.epub").apply { writeText("book with marks") }
        val r = writer.overwrite(Uri.fromFile(orig), sha(orig.readBytes()), new, sha(new.readBytes()), "t4")
        assertEquals(OriginalWriteResult.WRITTEN, r)
        assertEquals("book original", File(context.filesDir, "quick_backups/t4.epub").readText())
    }
}
