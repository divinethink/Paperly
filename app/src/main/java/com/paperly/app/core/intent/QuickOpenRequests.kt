package com.paperly.app.core.intent

import android.content.ContentResolver
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PDF_MIME = "application/pdf"
private const val EPUB_MIME = "application/epub+zip"

/** "Open with" (VIEW) of a PDF or EPUB: read it directly instead of importing. Share (SEND) still imports. */
fun isQuickOpen(intent: Intent?, resolver: ContentResolver): Boolean {
    val view = intent ?: return false
    if (view.action != Intent.ACTION_VIEW) return false
    val type = view.type ?: view.data?.let { runCatching { resolver.getType(it) }.getOrNull() }
    return type == PDF_MIME || type == EPUB_MIME
}

/** Activity -> navigation hand-off for [isQuickOpen] requests; a state holder so a cold-start request is not lost. */
@Singleton
class QuickOpenRequests @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun post(uri: String) {
        _pending.value = uri
    }

    /** Clears only if [uri] is still the pending one (a newer request is never dropped). */
    fun consume(uri: String) {
        _pending.compareAndSet(uri, null)
    }
}
