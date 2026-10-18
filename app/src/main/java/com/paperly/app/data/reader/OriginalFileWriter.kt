package com.paperly.app.data.reader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_DIR = "quick_backups"
private const val BUFFER = 64 * 1024
private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

enum class OriginalWriteResult { WRITTEN, NO_PERMISSION, CHANGED, FAILED }

/**
 * Replaces the bytes of an externally opened PDF (content URI) with a finished, verified copy.
 * Order: write permission -> original still unchanged? -> verified backup -> overwrite -> read back and compare.
 * A mismatch after the overwrite restores the backup. Nothing is touched when permission is missing.
 */
@Singleton
class OriginalFileWriter @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun overwrite(
        uri: Uri,
        expectedChecksum: String,
        newFile: File,
        newChecksum: String,
        backupName: String,
    ): OriginalWriteResult = withContext(Dispatchers.IO) {
        val pfd = openForWrite(uri) ?: return@withContext OriginalWriteResult.NO_PERMISSION
        val job = Job(uri, expectedChecksum, newFile, newChecksum, backupName)
        pfd.use { run(it, job) }
    }

    private class Job(
        val uri: Uri,
        val expectedChecksum: String,
        val newFile: File,
        val newChecksum: String,
        val backupName: String,
    )

    private fun run(pfd: ParcelFileDescriptor, job: Job): OriginalWriteResult {
        if (readChecksum(job.uri) != job.expectedChecksum) return OriginalWriteResult.CHANGED
        val extension = job.newFile.extension.ifEmpty { "pdf" }
        val backup = backupOf(job.uri, job.expectedChecksum, job.backupName, extension)
            ?: return OriginalWriteResult.FAILED
        val out = FileOutputStream(pfd.fileDescriptor).channel // not closed here: the descriptor is owned by [pfd]
        val ok = runCatching { replaceAll(out, job.newFile) }.isSuccess && readChecksum(job.uri) == job.newChecksum
        if (!ok) runCatching { replaceAll(out, backup) } // the original must never stay half-written
        return if (ok) OriginalWriteResult.WRITTEN else OriginalWriteResult.FAILED
    }

    private fun openForWrite(uri: Uri): ParcelFileDescriptor? =
        runCatching { context.contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()

    private fun readChecksum(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { sha256(it, null) }
    }.getOrNull()

    private fun backupOf(uri: Uri, checksum: String, name: String, extension: String): File? = runCatching {
        val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        pruneBackups(dir)
        val target = File(dir, "$name.$extension")
        val copied = context.contentResolver.openInputStream(uri)?.use { src ->
            target.outputStream().buffered().use { sha256(src, it) }
        }
        target.takeIf { copied == checksum }.also { if (it == null) target.delete() }
    }.getOrNull()

    private fun pruneBackups(dir: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        runCatching { dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() } }
    }
}

/** Overwrites the whole destination with [source] and cuts any longer leftover; throws on any I/O problem. */
private fun replaceAll(out: FileChannel, source: File) {
    FileInputStream(source).channel.use { src ->
        val size = src.size()
        out.position(0)
        var pos = 0L
        while (pos < size) pos += src.transferTo(pos, size - pos, out)
        out.truncate(size)
        out.force(true)
    }
}

private fun sha256(input: InputStream, copyTo: java.io.OutputStream?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(BUFFER)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        digest.update(buf, 0, n)
        copyTo?.write(buf, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
