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

    override fun finish(token: String, items: List<PdfAnnotationItem>, savedToLibrary: Boolean) {
        if (items.isEmpty()) {
            store.delete(token)
            return
        }
        scope.launch {
            val pristine = File(quickViewDir(context), "$token.pristine")
            try {
                lock.withLock { process(token, items, savedToLibrary, pristine)?.let(::toast) }
            } finally {
                pristine.delete()
                store.delete(token)
            }
        }
    }

    /** Message to show, or null when nothing was attempted (setting off / source unknown / nothing new). */
    internal suspend fun process(token: String, items: List<PdfAnnotationItem>, saved: Boolean, pristine: File): Int? {
        val source = store.source(token)
        val file = store.resolve(token)
        if (source == null || file == null || !settings.writeHighlights.first()) return null
        val keepable = !saved && runCatching { file.copyTo(pristine, overwrite = true) }.isSuccess // before any write
        return when (val result = step.run(token, source, file, items)) {
            null -> null
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
}
