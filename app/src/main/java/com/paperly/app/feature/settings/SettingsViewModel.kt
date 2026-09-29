package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.storage.StorageUsage
import com.paperly.app.domain.storage.StorageUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: StorageUsageRepository,
) : ViewModel() {
    /** null until the first emission (UI shows nothing rather than a false "0 B"). */
    val usage: StateFlow<StorageUsage?> = repository.observeUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
