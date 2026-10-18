package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubMarkWriter
import com.paperly.app.domain.reader.EpubXhtmlMarks
import com.paperly.app.domain.reader.PdfSkipReason
import com.paperly.app.domain.reader.PdfWriteResult
import com.paperly.app.domain.reader.XhtmlMark
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Puts EPUB marks into the book file as `<span>`s (pure Java zip, no new library). Chapters in another encoding,
 * and marks whose text is not found for sure, are skipped. Nothing here touches the database.
 */
@Singleton
class EpubFileMarkWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : EpubMarkWriter {

    override suspend fun write(
        file: File,
        marks: List<EpubAnnotation>,
        removeStale: Boolean,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult = withContext(Dispatchers.IO) {
        when {
            !file.isFile -> PdfWriteResult.Skipped(PdfSkipReason.NOT_FOUND)
            file.length() > MAX_BYTES -> PdfWriteResult.Skipped(PdfSkipReason.TOO_LARGE)
            marks.isEmpty() && !removeStale -> PdfWriteResult.NothingToWrite
            else -> guarded(file, marks, removeStale, verify)
        }
    }

    private suspend fun guarded(
        file: File,
        marks: List<EpubAnnotation>,
        removeStale: Boolean,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult {
        val tmp = File(file.parentFile, "${file.name}$TMP_SUFFIX")
        return try {
            attempt(file, tmp, marks, removeStale, verify)
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: OutOfMemoryError) {
            PdfWriteResult.Skipped(PdfSkipReason.OUT_OF_MEMORY)
        } catch (e: Exception) {
            PdfWriteResult.Failed(e)
        } finally {
            tmp.delete() // no-op after a successful move
        }
    }

    private suspend fun attempt(
        file: File,
        tmp: File,
        marks: List<EpubAnnotation>,
        removeStale: Boolean,
        verify: suspend (File) -> Boolean,
    ): PdfWriteResult {
        val edit = plan(file, marks, removeStale)
        if (edit.changed.isEmpty()) return PdfWriteResult.NothingToWrite
        rewriteEpub(file, tmp, edit.changed)
        if (!sameBookAsBefore(file, tmp, edit.changed.keys) || !verify(tmp)) {
            throw IOException("Verification of the new EPUB failed")
        }
        val checksum = sha256Hex(tmp)
        val size = tmp.length()
        pruneBackups()
        file.copyTo(File(backupDir(), "${file.name}$BACKUP_SUFFIX"), overwrite = true) // no backup = no replace
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return PdfWriteResult.Written(edit.added, checksum, size)
    }

    /** New chapter bytes by entry name, and how many marks were really added. */
    private class Plan(val changed: Map<String, ByteArray>, val added: Int)

    private fun plan(file: File, marks: List<EpubAnnotation>, removeStale: Boolean): Plan {
        val byChapter = marks.mapNotNull { toXhtmlMark(it, it.id) }.groupBy({ it.first }, { it.second })
        val keep = marks.map { it.id }.toSet()
        val changed = LinkedHashMap<String, ByteArray>()
        var added = 0
        ZipFile(file).use { zip ->
            val candidates = zip.entries().toList().filter {
                isChapter(it.name) && (removeStale || byChapter[it.name].orEmpty().isNotEmpty())
            }
            for (entry in candidates) {
                val wanted = byChapter[entry.name].orEmpty()
                val text = utf8Chapter(zip.getInputStream(entry).use { it.readBytes() }) ?: continue
                val updated = updatedChapter(text, wanted, if (removeStale) keep else null)
                if (updated != text) {
                    changed[entry.name] = updated.toByteArray(Charsets.UTF_8)
                    added += (EpubXhtmlMarks.idsIn(updated) - EpubXhtmlMarks.idsIn(text)).size
                }
            }
        }
        return Plan(changed, added)
    }

    /** [keep] = ids that stay; our other marks in the chapter go. Null = leave other marks alone. */
    private fun updatedChapter(text: String, wanted: List<XhtmlMark>, keep: Set<String>?): String {
        val stale = if (keep == null) emptySet() else EpubXhtmlMarks.idsIn(text) - keep
        return EpubXhtmlMarks.apply(EpubXhtmlMarks.remove(text, stale), wanted)
    }

    private fun backupDir(): File = File(context.filesDir, PdfBoxAnnotationWriter.BACKUP_DIR).apply { mkdirs() }

    private fun pruneBackups() {
        val cutoff = System.currentTimeMillis() - BACKUP_KEEP_MS
        backupDir().listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private companion object {
        const val MAX_BYTES = 100L * 1024 * 1024
        const val BACKUP_SUFFIX = ".bak"
        const val BACKUP_KEEP_MS = 7L * 24 * 60 * 60 * 1000
        const val TMP_SUFFIX = ".epubmarks.tmp" // ends with ".tmp": OrphanFiles removes a stale one
    }
}

private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private const val BUFFER_SIZE = 64 * 1024
