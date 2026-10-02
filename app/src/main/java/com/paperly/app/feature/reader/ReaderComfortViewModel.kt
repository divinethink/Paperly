package com.paperly.app.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.reader.ReaderComfort
import com.paperly.app.domain.reader.ReaderComfortStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SAVE_DELAY_MS = 300L

/** Live comfort state (slider drags apply at once); persisted after a short pause so drags do not spam DataStore. */
@HiltViewModel
class ReaderComfortViewModel @Inject constructor(
    private val store: ReaderComfortStore,
) : ViewModel() {
    private val state = MutableStateFlow(ReaderComfort())
    val comfort: StateFlow<ReaderComfort> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = store.comfort.first()
            state.drop(1).collectLatest {
                delay(SAVE_DELAY_MS)
                store.save(it)
            }
        }
    }

    fun update(value: ReaderComfort) {
        state.value = value.clamped()
    }
}
