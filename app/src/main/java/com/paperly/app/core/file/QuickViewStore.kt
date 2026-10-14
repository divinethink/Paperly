package com.paperly.app.core.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val QUICK_DIR = "quickview"
private const val QUICK_PREFIX = "quick-"
private const val BUFFER_SIZE = 64 * 1024
private const val HEADER_BYTES = 1024
private const val MAX_TITLE = 200
private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

/** Cache folder of "Quick view" PDFs (opened without importing); old leftovers are pruned at app start. */
fun quickViewDir(context: Context): File = File(context.cacheDir, QUICK_DIR)

/** Outcome of staging an externally opened PDF. */
sealed interface QuickStage {
    /** Same content is already in the Library: open that copy (progress and bookmarks included). */
    data class Library(val documentId: String) : QuickStage

    /** A temporary cache copy; [token] is the Reader route id (never a Library id). */
    data class Temp(val token: String) : QuickStage
    data object NotPdf : QuickStage
    data object Failed : QuickStage
}

/**
 * Open-With for a PDF: the file is copied to the cache (never to the Library), checksum-matched against the Library,
 * and read from there. Nothing is written to the database; the cache copy dies with the Reader.
 */
@Singleton
class QuickViewStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentDao,
) {
    private val dir: File get() = quickViewDir(context).apply { mkdirs() }

    /** Removes leftovers of earlier sessions (e.g. the app was killed while a quick PDF was open). Best-effort. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        runCatching { dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() } }
    }

    suspend fun stage(sourceUri: String): QuickStage = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val name = runCatching { displayName(uri) }.getOrNull()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val isPdf = name?.endsWith(".pdf", ignoreCase = true) == true || mime == "application/pdf"
        if (!isPdf) return@withContext QuickStage.NotPdf
        val token = QUICK_PREFIX + UUID.randomUUID()
        try {
            val checksum = copyPdf(uri, File(dir, "$token.pdf"))
            val existing = dao.findActiveByChecksum(checksum)
            if (existing != null) {
                delete(token)
                QuickStage.Library(existing.documentId)
            } else {
                val title = name?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "Document" }.take(MAX_TITLE)
                runCatching { File(dir, "$token.txt").writeText(title) } // best-effort; title falls back
                QuickStage.Temp(token)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            delete(token)
            throw e
        } catch (e: Exception) {
            delete(token) // any failure (I/O, permission): no half copy is left behind
            QuickStage.Failed
        }
    }

    /** In-memory Document for the Reader (no database row exists). */
    fun document(token: String): Document? {
        val file = resolve(token) ?: return null
        val title = runCatching { File(dir, "$token.txt").readText() }.getOrNull()?.ifBlank { null } ?: "Document"
        return Document(token, title, DocumentType.PDF, file.length(), file.lastModified())
    }

    fun resolve(token: String): File? = if (isToken(token)) File(dir, "$token.pdf").takeIf { it.isFile } else null

    fun delete(token: String) {
        if (!isToken(token)) return
        runCatching { File(dir, "$token.pdf").delete() }
        runCatching { File(dir, "$token.txt").delete() }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /**
     * Copies to [target] and returns the SHA-256. Throws if the copy is not a PDF; the caller removes the partial
     * file ([stage] deletes the whole token on any failure).
     */
    private suspend fun copyPdf(uri: Uri, target: File): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open source")
        val digest = MessageDigest.getInstance("SHA-256")
        val size = input.use { src -> target.outputStream().buffered().use { out -> pump(src, out, digest) } }
        if (target.length() != size || !hasPdfHeader(target)) throw IOException("Not a valid PDF copy")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun isToken(id: String): Boolean = id.startsWith(QUICK_PREFIX) && SAFE_DOCUMENT_ID.matches(id)
    }
}

private fun hasPdfHeader(file: File): Boolean = file.inputStream().use { input ->
    val head = ByteArray(HEADER_BYTES)
    val n = input.read(head)
    n > 0 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
}

private suspend fun pump(src: InputStream, out: OutputStream, digest: MessageDigest): Long {
    val buf = ByteArray(BUFFER_SIZE)
    var size = 0L
    while (true) {
        currentCoroutineContext().ensureActive() // a top-level function has no CoroutineScope receiver
        val n = src.read(buf)
        if (n < 0) return size
        out.write(buf, 0, n)
        digest.update(buf, 0, n)
        size += n
    }
}
