package com.paperly.app.data.scanner

import android.content.Context
import com.paperly.app.data.reader.PdfBoxAnnotationWriter
import com.paperly.app.domain.reader.LibraryPdfFiles
import com.paperly.app.domain.reader.PdfOpenCheck
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last steps of a page edit: verify the finished temp PDF, back up the current Library file (same folder and 7-day
 * rule as the highlight writer), atomically replace it, record checksum/size. Throws before the replace on any
 * problem, so the original is never changed by a failure.
 */
@Singleton
class ScanPdfReplacer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: LibraryPdfFiles,
    private val openCheck: PdfOpenCheck,
    private val ocr: ScanOcrRepository,
) {
    suspend fun replace(documentId: String, file: File, tmp: File, expectedPages: Int) {
        val pages = PDDocument.load(tmp, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        val countOk = pages == expectedPages
        if (!countOk || !openCheck.opens(tmp)) throw IOException("Verification of the new PDF failed")
        val checksum = sha256Hex(tmp)
        val bytes = tmp.length()
        val backups = File(context.filesDir, PdfBoxAnnotationWriter.BACKUP_DIR).apply { mkdirs() }
        pruneBackups(backups)
        file.copyTo(File(backups, "${file.name}.bak"), overwrite = true) // no backup = no replace
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        files.replaced(documentId, checksum, bytes)
        ocr.invalidate(documentId) // pages changed: old text no longer matches; read again if the switch is ON
    }

    private fun pruneBackups(dir: File) {
        val cutoff = System.currentTimeMillis() - BACKUP_KEEP_MS
        dir.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BACKUP_KEEP_MS = 7L * 24 * 60 * 60 * 1000
        const val BUFFER = 8192
    }
}
