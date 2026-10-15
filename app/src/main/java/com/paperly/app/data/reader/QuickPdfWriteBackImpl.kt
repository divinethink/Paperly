package com.paperly.app.data.reader

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.paperly.app.R
import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.core.file.QuickViewStore
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationWriter
import com.paperly.app.domain.reader.PdfOpenCheck
import com.paperly.app.domain.reader.PdfWriteResult
import com.paperly.app.domain.reader.QuickPdfWriteBack
import com.paperly.app.domain.settings.PdfFileWriteSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Step 4b: highlights made in Quick view go into the original file when the setting is on. They are first written
 * into the temporary copy (same safe writer as the Library), and only a verified copy replaces the original, which
 * is backed up first. No write permission / changed original / any failure: the original stays as it was and a
 * short message says so. The temporary copy is always removed at the end.
 */
@Singleton
class QuickPdfWriteBackImpl @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val store: QuickViewStore,
    private val writer: PdfAnnotationWriter,
    private val openCheck: PdfOpenCheck,
    private val settings: PdfFileWriteSettings,
    private val original: OriginalFileWriter,
) : QuickPdfWriteBack {
    private val lock = Mutex()

    override fun finish(token: String, items: List<PdfAnnotationItem>) {
        if (items.isEmpty()) {
            store.delete(token)
            return
        }
        scope.launch {
            try {
                lock.withLock { process(token, items)?.let(::toast) }
            } finally {
                store.delete(token)
            }
        }
    }

    /** Message to show, or null when nothing was attempted (setting off / source unknown / nothing new). */
    internal suspend fun process(token: String, items: List<PdfAnnotationItem>): Int? {
        val source = store.source(token)
        val file = store.resolve(token)
        if (source == null || file == null || !settings.writeHighlights.first()) return null
        // Ids made unique per session, so a later session on the same original never collides with these /NM keys.
        val unique = items.map { it.copy(id = "$token-${it.id}") }
        val result = writer.write(file, unique) { openCheck.opens(it) }
        return when (result) {
            is PdfWriteResult.NothingToWrite -> null
            is PdfWriteResult.Written -> messageFor(
                original.overwrite(Uri.parse(source.uri), source.checksum, file, result.checksum, token),
            )
            else -> R.string.quick_write_failed
        }
    }

    private fun messageFor(result: OriginalWriteResult): Int = when (result) {
        OriginalWriteResult.WRITTEN -> R.string.quick_write_ok
        OriginalWriteResult.NO_PERMISSION -> R.string.quick_write_readonly
        OriginalWriteResult.CHANGED -> R.string.quick_write_changed
        OriginalWriteResult.FAILED -> R.string.quick_write_failed
    }

    private fun toast(@StringRes message: Int) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }
}
