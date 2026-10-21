package com.paperly.app.core.debug

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** TEMPORARY debug trace (import list bug). Delete this file and every ImportDebug.log call once fixed. */
object ImportDebug {
    private val state = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = state.asStateFlow()

    fun log(msg: String) {
        val time = SimpleDateFormat("mm:ss.SSS", Locale.US).format(Date())
        Log.d("PaperlyDbg", msg)
        state.update { (it + "$time $msg").takeLast(16) }
    }
}
