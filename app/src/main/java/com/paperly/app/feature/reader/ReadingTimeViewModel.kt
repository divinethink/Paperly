package com.paperly.app.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.reader.ReadingTimeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MS = 5_000L

@HiltViewModel
class ReadingTimeViewModel @Inject constructor(
    private val store: ReadingTimeStore,
) : ViewModel() {
    val todayMinutes: StateFlow<Int> = store.todayMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** NonCancellable: the last span is written even if this ViewModel is cleared right as the reader closes. */
    fun record(seconds: Long) {
        viewModelScope.launch { withContext(NonCancellable) { store.addSeconds(seconds) } }
    }
}
