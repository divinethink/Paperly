package com.paperly.app.data.reader

import android.content.Context
import android.net.Uri
import com.paperly.app.R
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.file.quickViewDir
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubMarkWriter
import com.paperly.app.domain.reader.EpubOpenCheck
import com.paperly.app.domain.reader.PdfWriteResult
import com.paperly.app.domain.reader.QuickEpubWriteBack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Quick-view EPUB: marks into a separate copy, or into the original via a verified copy (original backed up first). */
@Singleton
class QuickEpubWriteBackImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: QuickViewStore,
    private val writer: EpubMarkWriter,
    private val openCheck: EpubOpenCheck,
    private val original: OriginalFileWriter,
) : QuickEpubWriteBack {
    private val lock = Mutex()

    override suspend fun markedCopy(token: String, marks: List<EpubAnnotation>): File? = lock.withLock {
        val file = store.resolve(token)
        val out = File(quickViewDir(context), "$token$MARKED_SUFFIX")
        if (file != null && marks.isNotEmpty() && mark(token, file, out, marks) is PdfWriteResult.Written) out else null
    }

    override suspend fun replaceNow(token: String, marks: List<EpubAnnotation>): Int = lock.withLock {
        val source = store.source(token)
        val file = store.resolve(token)
        val work = File(quickViewDir(context), "$token$REPLACE_SUFFIX")
        try {
            when {
                source == null || file == null -> R.string.quick_write_nosource
                marks.isEmpty() -> R.string.quick_write_nothing
                else -> when (val result = mark(token, file, work, marks)) {
                    is PdfWriteResult.Written -> overwrite(token, source.uri, source.checksum, work, result.checksum)
                    is PdfWriteResult.NothingToWrite -> R.string.quick_write_nothing
                    else -> R.string.quick_write_failed
                }
            }
        } finally {
            work.delete()
        }
    }

    override fun release(token: String) {
        File(quickViewDir(context), "$token$MARKED_SUFFIX").delete()
        File(quickViewDir(context), "$token$REPLACE_SUFFIX").delete()
    }

    private suspend fun overwrite(token: String, uri: String, checksum: String, work: File, newChecksum: String): Int {
        // its own backup name, so a second replace never overwrites the backup of the true original
        val backup = "$token-${System.currentTimeMillis()}"
        return when (original.overwrite(Uri.parse(uri), checksum, work, newChecksum, backup)) {
            OriginalWriteResult.WRITTEN -> {
                store.updateChecksum(token, newChecksum)
                R.string.quick_replace_ok
            }
            OriginalWriteResult.NO_PERMISSION -> R.string.quick_replace_no_permission
            OriginalWriteResult.CHANGED -> R.string.quick_replace_changed
            OriginalWriteResult.FAILED -> R.string.quick_write_failed
        }
    }

    /** [file] copied to [target] with the marks written in; null = copy failed. [target] is removed unless Written. */
    private suspend fun mark(token: String, file: File, target: File, marks: List<EpubAnnotation>): PdfWriteResult? {
        if (runCatching { file.copyTo(target, overwrite = true) }.isFailure) return null
        // ids unique per session: a later session on the same book never collides with these ids in the file
        val unique = marks.map { it.copy(id = "$token-${it.id}") }
        val result = writer.write(target, unique, removeStale = false) { openCheck.opens(it) }
        if (result !is PdfWriteResult.Written) target.delete()
        return result
    }

    private companion object {
        const val MARKED_SUFFIX = ".marked.epub"
        const val REPLACE_SUFFIX = ".replace.epub"
    }
}
