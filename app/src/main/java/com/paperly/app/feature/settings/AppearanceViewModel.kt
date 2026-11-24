package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.settings.AppPalette
import com.paperly.app.domain.settings.AppThemeMode
import com.paperly.app.domain.settings.AppearanceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val store: AppearanceStore,
) : ViewModel() {
    val dynamicColor: StateFlow<Boolean> =
        store.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<AppThemeMode> =
        store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    val palette: StateFlow<AppPalette> =
        store.palette.stateIn(viewModelScope, SharingStarted.Eagerly, AppPalette.PAPERLY)

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch { store.setPalette(palette) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { store.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { store.setDynamicColor(enabled) }
    }
}
