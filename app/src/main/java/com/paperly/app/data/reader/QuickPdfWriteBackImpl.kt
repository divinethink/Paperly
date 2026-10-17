package com.paperly.app.data.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.paperly.app.R
import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.core.file.quickViewDir
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.QuickPdfWriteBack
import com.paperly.app.domain.settings.PdfFileWriteSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Quick view, Reader closed (setting on): highlights go into the original file via a verified copy (original backed
 * up first). If Android does not allow that (or anything fails) the original stays untouched and the untouched PDF
 * is kept in the Library with its highlights instead, so they are never lost. Temporary files are always removed.
 */
@Singleton
class QuickPdfWriteBackImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val store: QuickViewStore,
    private val settings: PdfFileWriteSettings,
    private val step: QuickOriginalStep,
    private val fallback: QuickLibraryFallback,
) : QuickPdfWriteBack {
    private val lock = Mutex()
    private val replaced = ConcurrentHashMap<String, List<PdfAnnotationItem>>() // what "Replace" already wrote

    override fun finish(token: String, items: List<PdfAnnotationItem>, savedToLibrary: Boolean) {
        val alreadyInFile = replaced.remove(token) == items
        if (items.isEmpty() || alreadyInFile) {
            dropTemporaryFiles(token)
            return
        }
        scope.launch {
            val pristine = File(quickViewDir(context), "$token.pristine")
            try {
                lock.withLock { process(token, items, savedToLibrary, pristine)?.let(::toast) }
            } finally {
                pristine.delete()
                dropTemporaryFiles(token)
            }
        }
    }

    override suspend fun markedCopy(token: String, items: List<PdfAnnotationItem>): File? = lock.withLock {
        val file = store.resolve(token)
        val out = File(quickViewDir(context), "$token$MARKED_SUFFIX")
        if (file != null && items.isNotEmpty() && step.markedCopy(token, file, out, items)) out else null
    }

    override suspend fun replaceNow(token: String, items: List<PdfAnnotationItem>): Int = lock.withLock {
        val source = store.source(token)
        val file = store.resolve(token)
        val work = File(quickViewDir(context), "$token$REPLACE_SUFFIX")
        try {
            when {
                source == null || file == null -> R.string.quick_write_nosource
                items.isEmpty() -> R.string.quick_write_nothing
                runCatching { file.copyTo(work, overwrite = true) }.isFailure -> R.string.quick_write_failed
                else -> {
                    // its own backup name: the first one holds the true original and must not be overwritten
                    val backup = "$token-${System.currentTimeMillis()}"
                    replaceMessage(step.run(token, source, work, items, backup)).also {
                        if (it == R.string.quick_replace_ok) replaced[token] = items
                    }
                }
            }
        } finally {
            work.delete()
        }
    }

    private fun replaceMessage(result: OriginalWriteResult?): Int = when (result) {
        OriginalWriteResult.WRITTEN -> R.string.quick_replace_ok
        OriginalWriteResult.NO_PERMISSION -> R.string.quick_replace_no_permission
        OriginalWriteResult.CHANGED -> R.string.quick_replace_changed
        null -> R.string.quick_write_nothing
        else -> R.string.quick_write_failed
    }

    private fun dropTemporaryFiles(token: String) {
        File(quickViewDir(context), "$token$MARKED_SUFFIX").delete()
        File(quickViewDir(context), "$token$REPLACE_SUFFIX").delete()
        store.delete(token)
    }

    /** Message to show; every outcome (also "nothing attempted") says why, so highlights never vanish silently. */
    internal suspend fun process(token: String, items: List<PdfAnnotationItem>, saved: Boolean, pristine: File): Int? {
        val source = store.source(token)
        val file = store.resolve(token)
        if (source == null || file == null) return R.string.quick_write_nosource
        if (!settings.writeHighlights.first()) return R.string.quick_write_off
        val keepable = !saved && runCatching { file.copyTo(pristine, overwrite = true) }.isSuccess // before any write
        return when (step.run(token, source, file, items)) {
            null -> R.string.quick_write_nothing
            OriginalWriteResult.WRITTEN -> R.string.quick_write_ok
            else -> when {
                saved -> R.string.quick_write_failed // the Library copy already holds the highlights
                keepable && fallback.keep(token, pristine, items) -> R.string.quick_write_library
                else -> R.string.quick_write_failed
            }
        }
    }

    private fun toast(@StringRes message: Int) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val MARKED_SUFFIX = ".marked.pdf"
        const val REPLACE_SUFFIX = ".replace.pdf"
    }
}
