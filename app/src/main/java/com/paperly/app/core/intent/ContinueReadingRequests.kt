package com.paperly.app.core.intent

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Action of the launcher "Continue Reading" shortcut (see res/xml/shortcuts.xml). */
const val ACTION_CONTINUE_READING = "com.paperly.app.action.CONTINUE_READING"

fun isContinueReading(intent: Intent?): Boolean = intent?.action == ACTION_CONTINUE_READING

/**
 * Hand-off from the Activity to navigation, a state (not an event) like [IncomingImportRequests]:
 * a shortcut tapped during a cold start is still pending when the nav graph is ready.
 * The target document is resolved when consumed (the most recently opened one), so it can never be stale.
 */
@Singleton
class ContinueReadingRequests @Inject constructor() {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun post() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
