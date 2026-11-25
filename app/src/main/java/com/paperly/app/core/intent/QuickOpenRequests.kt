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

    // True when this request is what started the Activity (not a new file arriving in an Activity that is open).
    @Volatile
    var startedActivity: Boolean = false
        private set

    // Reader id opened by such a request: leaving it closes the Activity, back to the app the file came from.
    @Volatile
    private var closeOnLeaveId: String? = null

    fun post(uri: String, startedActivity: Boolean = false) {
        this.startedActivity = startedActivity
        closeOnLeaveId = null
        _pending.value = uri
    }

    fun markCloseOnLeave(readerId: String) {
        closeOnLeaveId = readerId
    }

    /** Single use: asked once when the Reader first appears, so a later normal open of the same file is unaffected. */
    fun takeCloseOnLeave(readerId: String): Boolean {
        val match = closeOnLeaveId == readerId
        if (match) closeOnLeaveId = null
        return match
    }

    /** Clears only if [uri] is still the pending one (a newer request is never dropped). */
    fun consume(uri: String) {
        _pending.compareAndSet(uri, null)
    }
}
