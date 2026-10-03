package com.paperly.app.feature.settings

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.ConflictInfo
import com.paperly.app.domain.sync.ConflictResolver
import com.paperly.app.domain.sync.FileSyncCounts
import com.paperly.app.domain.sync.SyncQueue
import com.paperly.app.domain.sync.SyncQueueCounts
import com.paperly.app.domain.sync.SyncSettings
import com.paperly.app.domain.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [consent] is set while Drive access still has to be allowed; [consentDenied] after the user said no. */
data class SyncAccountUiState(
    val busy: Boolean = false,
    val result: SignInResult? = null,
    val consent: IntentSender? = null,
    val consentDenied: Boolean = false,
    val resolveFailed: Boolean = false,
)

@HiltViewModel
class SyncAccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val syncQueue: SyncQueue,
    private val driveAuth: DriveAuth,
    private val syncSettings: SyncSettings,
    private val conflictResolver: ConflictResolver,
    syncStatus: SyncStatus,
) : ViewModel() {
    /** null until the first emission, so the UI never flashes a wrong "signed out". */
    val account: StateFlow<AuthState?> = auth.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val wifiOnly: StateFlow<Boolean> = syncSettings.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val paused: StateFlow<Boolean> = syncSettings.paused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val fileCounts: StateFlow<FileSyncCounts> = syncStatus.fileCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FileSyncCounts(0, 0))

    val queueCounts: StateFlow<SyncQueueCounts> = syncStatus.queueCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncQueueCounts(0, 0, 0))

    val conflicts: StateFlow<List<ConflictInfo>> = syncStatus.conflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _ui = MutableStateFlow(SyncAccountUiState())
    val ui: StateFlow<SyncAccountUiState> = _ui

    fun signIn(activity: Context) {
        if (_ui.value.busy) return // double-tap guard
        _ui.update { it.copy(busy = true, result = null) }
        viewModelScope.launch {
            val result = auth.signIn(activity)
            if (result == SignInResult.SIGNED_IN) syncQueue.kick()
            _ui.update { it.copy(busy = false, result = result.takeIf { r -> r != SignInResult.SIGNED_IN }) }
        }
    }

    fun signOut(activity: Context) {
        viewModelScope.launch {
            auth.signOut(activity)
            _ui.value = SyncAccountUiState()
        }
    }

    /** Asks whether Drive access is already allowed; if not, [SyncAccountUiState.consent] is filled. */
    fun refreshAccess() {
        viewModelScope.launch { apply(driveAuth.token(), afterConsent = false) }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch { apply(driveAuth.tokenFromConsent(data), afterConsent = true) }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            syncSettings.setWifiOnly(value)
            syncQueue.kick() // re-schedule with the new network rule
        }
    }

    fun setPaused(value: Boolean) {
        viewModelScope.launch {
            syncSettings.setPaused(value)
            syncQueue.kick()
        }
    }

    fun retryFailed() {
        viewModelScope.launch { syncQueue.retryFailed() }
    }

    fun keepThisDevice(documentId: String) = resolve { conflictResolver.keepThisDevice(documentId) }

    fun keepCloud(documentId: String) = resolve { conflictResolver.keepCloud(documentId) }

    private fun resolve(action: suspend () -> Boolean) {
        viewModelScope.launch {
            val ok = action()
            _ui.update { it.copy(resolveFailed = !ok) }
            if (ok) syncQueue.kick()
        }
    }

    private suspend fun apply(token: DriveToken, afterConsent: Boolean) {
        when (token) {
            is DriveToken.Ready -> {
                _ui.update { it.copy(consent = null, consentDenied = false) }
                if (afterConsent) syncQueue.resumeDeferred()
            }
            is DriveToken.NeedsConsent -> _ui.update { it.copy(consent = token.request, consentDenied = afterConsent) }
            DriveToken.Unavailable -> _ui.update { it.copy(consent = null, consentDenied = afterConsent) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
