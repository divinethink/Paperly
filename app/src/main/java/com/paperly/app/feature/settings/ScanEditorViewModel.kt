package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.scanner.ScanEditorSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScanEditorViewModel @Inject constructor(
    private val settings: ScanEditorSettings,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.setEnabled(value) }
    }
}
