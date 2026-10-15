package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.ocr.PageOcr
import com.paperly.app.domain.scanner.ScanOcrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanOcrUi(val enabled: Boolean = false, val downloading: Boolean = false, val failed: Boolean = false)

/** S3 switch: turning it ON fetches the recognition data once (existing download), then queues the existing scans. */
@HiltViewModel
class ScanOcrViewModel @Inject constructor(
    private val repository: ScanOcrRepository,
    private val ocr: PageOcr,
) : ViewModel() {
    private val downloading = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    val ui: StateFlow<ScanOcrUi> = combine(repository.enabled, downloading, failed) { on, busy, bad ->
        ScanOcrUi(on, busy, bad)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScanOcrUi())

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            failed.value = false
            repository.setEnabled(value)
            if (value) prepare()
        }
    }

    private suspend fun prepare() {
        if (!ocr.modelReady.value) {
            downloading.value = true
            val ok = ocr.downloadModel()
            downloading.value = false
            if (!ok) {
                failed.value = true
                repository.setEnabled(false) // the switch must not claim OCR works without its data
                return
            }
        }
        repository.scheduleAll()
    }
}
