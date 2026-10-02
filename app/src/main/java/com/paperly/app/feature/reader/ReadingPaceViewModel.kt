package com.paperly.app.feature.reader

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.paperly.app.domain.reader.ReadingPace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the session pace across bar show/hide (the bars leave composition, this does not). */
@HiltViewModel
class ReadingPaceViewModel @Inject constructor() : ViewModel() {
    private val pace = ReadingPace()
    private val _minutesLeft = MutableStateFlow<Int?>(null)
    val minutesLeft: StateFlow<Int?> = _minutesLeft.asStateFlow()

    /** [fraction] = whole-document progress, 0..1. */
    fun record(fraction: Float) {
        val value = fraction.coerceIn(0f, 1f)
        pace.record(value, SystemClock.elapsedRealtime())
        _minutesLeft.value = pace.minutesLeft(value)
    }
}
