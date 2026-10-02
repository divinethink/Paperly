package com.paperly.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncAccountUiState(val busy: Boolean = false, val result: SignInResult? = null)

@HiltViewModel
class SyncAccountViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    /** null until the first emission, so the UI never flashes a wrong "signed out". */
    val account: StateFlow<AuthState?> = auth.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _ui = MutableStateFlow(SyncAccountUiState())
    val ui: StateFlow<SyncAccountUiState> = _ui

    fun signIn(activity: Context) {
        if (_ui.value.busy) return // double-tap guard
        _ui.value = SyncAccountUiState(busy = true)
        viewModelScope.launch {
            val result = auth.signIn(activity)
            _ui.update { SyncAccountUiState(busy = false, result = result.takeIf { it != SignInResult.SIGNED_IN }) }
        }
    }

    fun signOut(activity: Context) {
        viewModelScope.launch {
            auth.signOut(activity)
            _ui.value = SyncAccountUiState()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
