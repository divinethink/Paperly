package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.settings.PdfFileWriteSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PdfFileWriteViewModel @Inject constructor(
    private val settings: PdfFileWriteSettings,
) : ViewModel() {
    val enabled: StateFlow<Boolean> =
        settings.writeHighlights.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.setWriteHighlights(value) }
    }
}
