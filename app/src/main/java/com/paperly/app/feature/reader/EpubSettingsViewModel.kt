package com.paperly.app.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.EpubTypographyStore
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** EPUB typography + reader theme (split from EpubReaderViewModel to keep constructors small). */
@HiltViewModel
class EpubSettingsViewModel @Inject constructor(
    private val typographyStore: EpubTypographyStore,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {
    val typography: StateFlow<EpubTypography> =
        typographyStore.typography.stateIn(viewModelScope, SharingStarted.Eagerly, EpubTypography())
    val theme: StateFlow<ReaderTheme> =
        readerPreferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.AUTO)

    fun updateTypography(value: EpubTypography) {
        viewModelScope.launch { typographyStore.save(value) }
    }

    fun setTheme(value: ReaderTheme) {
        viewModelScope.launch { readerPreferences.setTheme(value) }
    }
}
