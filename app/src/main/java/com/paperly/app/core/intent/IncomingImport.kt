package com.paperly.app.core.intent

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Importable URI from an Open-With (VIEW) or Share (SEND) intent, else null.
 * Only content:// is accepted: file:// could point into other apps' private storage.
 */
fun extractImportUri(intent: Intent?): String? {
    if (intent == null) return null
    val uri: Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
    return uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }?.toString()
}

/**
 * Hand-off from the Activity (which receives the intent) to the Library (which owns the import flow).
 * A state holder, not an event: an import requested during a cold start is still there when Library appears.
 */
@Singleton
class IncomingImportRequests @Inject constructor() {
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
