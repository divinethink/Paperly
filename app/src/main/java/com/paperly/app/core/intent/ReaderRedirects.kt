package com.paperly.app.core.intent

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reader -> navigation hand-off: a quick-view PDF was saved to the Library; reopen it there (same page). */
@Singleton
class ReaderRedirects @Inject constructor() {
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target.asStateFlow()

    fun post(documentId: String) {
        _target.value = documentId
    }

    fun consume(documentId: String) {
        _target.compareAndSet(documentId, null)
    }
}
