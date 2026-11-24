package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.reader.ReaderPreferences
import com.paperly.app.domain.reader.ReaderTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The reading theme (page colours) is the same global choice the Reader menu changes. */
@HiltViewModel
class ReadingThemeViewModel @Inject constructor(
    private val preferences: ReaderPreferences,
) : ViewModel() {
    val theme: StateFlow<ReaderTheme> =
        preferences.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.AUTO)

    fun setTheme(value: ReaderTheme) {
        viewModelScope.launch { preferences.setTheme(value) }
    }
}
