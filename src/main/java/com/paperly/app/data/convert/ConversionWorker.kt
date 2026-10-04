package com.paperly.app.data.convert

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paperly.app.data.reader.EpubPublicationOpener
import com.paperly.app.domain.converter.ConversionError
import com.paperly.app.domain.converter.ConversionOutcome
import com.paperly.app.domain.converter.ConvertOptions
import com.paperly.app.domain.converter.DocxToEpub
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private const val BUFFER_BYTES = 64 * 1024
private const val MAX_SOURCE_BYTES = 150L * 1024 * 1024 // cap on the copied DOCX so a huge file cannot fill the cache

/**
 * DOCX -> EPUB in the background. The picked file is only read (copied to app cache); the EPUB is written to
 * `out.epub.part`, renamed, opened with Readium, and only then added to the Library (`convertedFrom = "docx"`).
 * Anything that fails leaves the Library untouched and the cache folder is wiped. Hilt via EntryPoint (no hilt-work).
 */
class ConversionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun documents(): DocumentRepository
        fun epubOpener(): EpubPublicationOpener
    }

    private val entry by lazy { EntryPointAccessors.fromApplication(applicationContext, Entry::class.java) }

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { DEFAULT_TITLE }
        val dir = File(applicationContext.cacheDir, DIR).apply { mkdirs() }
        wipe(dir)
        return try {
            if (uri.isBlank()) fail(ERROR_SOURCE) else convertAndStore(Uri.parse(uri), title, dir)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            fail(ConversionError.UNREADABLE.name)
        } catch (e: Exception) {
            fail(ERROR_UNEXPECTED) // never crash the app from a background conversion; the Library is untouched
        } finally {
            wipe(dir)
        }
    }

    private suspend fun convertAndStore(uri: Uri, title: String, dir: File): Result {
        val epub = File(dir, "out.epub")
        val error = copySource(uri, File(dir, "source.docx"))
            ?: convert(File(dir, "source.docx"), File(dir, "out.epub.part"), title)
            ?: validate(File(dir, "out.epub.part"), epub)
        return if (error != null) fail(error) else store(epub, title)
    }

    /** Null = ok. The source is capped so a huge/odd file cannot fill the cache. */
    private fun copySource(uri: Uri, target: File): String? {
        val input = applicationContext.contentResolver.openInputStream(uri) ?: return ERROR_SOURCE
        val tooLarge = input.use { src -> FileOutputStream(target).use { out -> exceedsLimit(src, out) } }
        return if (tooLarge) ConversionError.TOO_LARGE.name else null
    }

    private fun convert(source: File, part: File, title: String): String? {
        val options = ConvertOptions(title, "urn:uuid:${UUID.randomUUID()}", utcNow())
        val outcome = FileOutputStream(part).buffered().use { DocxToEpub.convert(source, it, options) }
        return (outcome as? ConversionOutcome.Failure)?.error?.name
    }

    /** Rename only after the whole file is written, then prove Readium can open it (and it has content). */
    private suspend fun validate(part: File, epub: File): String? {
        val ok = part.renameTo(epub) && isReadable(epub)
        return if (ok) null else ERROR_INVALID
    }

    private suspend fun isReadable(epub: File): Boolean {
        val publication = entry.epubOpener().open(epub) ?: return false
        return try {
            publication.readingOrder.isNotEmpty()
        } finally {
            publication.close()
        }
    }

    private suspend fun store(epub: File, title: String): Result =
        when (val result = entry.documents().importConverted(Uri.fromFile(epub).toString(), title, CONVERTED_FROM)) {
            is ImportResult.Success -> Result.success(workDataOf(KEY_DOCUMENT_ID to result.documentId))
            is ImportResult.Duplicate -> Result.success(workDataOf(KEY_DUPLICATE_TITLE to result.existingTitle))
            else -> fail(ERROR_STORE)
        }

    private fun fail(error: String): Result = Result.failure(workDataOf(KEY_ERROR to error))

    private fun wipe(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /** Input: content:// URI string of the picked DOCX, and the title for the new EPUB. */
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"

        /** Output on success: [KEY_DOCUMENT_ID] (new book) or [KEY_DUPLICATE_TITLE] (same EPUB already in Library). */
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_DUPLICATE_TITLE = "duplicateTitle"

        /** Output on failure: a [ConversionError] name, or one of the ERROR_* codes below. */
        const val KEY_ERROR = "error"
        const val ERROR_SOURCE = "SOURCE"
        const val ERROR_INVALID = "INVALID"
        const val ERROR_STORE = "STORE"
        const val ERROR_UNEXPECTED = "UNEXPECTED"
        const val CONVERTED_FROM = "docx"
        private const val DIR = "convert"
        private const val DEFAULT_TITLE = "Document"
    }
}

private fun exceedsLimit(src: InputStream, out: OutputStream): Boolean {
    val buffer = ByteArray(BUFFER_BYTES)
    var total = 0L
    while (true) {
        val read = src.read(buffer)
        if (read < 0) return false
        total += read
        if (total > MAX_SOURCE_BYTES) return true
        out.write(buffer, 0, read)
    }
}

private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date())
