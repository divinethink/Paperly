package com.paperly.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.backup.BackupDestinationRunner
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.domain.backup.BackupTarget
import com.paperly.app.domain.backup.DestinationState
import com.paperly.app.domain.backup.LastBackup
import com.paperly.app.domain.backup.StoredBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/** Thin view of [BackupDestinationRunner]: the work lives in the app scope and survives leaving Settings. */
@HiltViewModel
class BackupDestinationViewModel @Inject constructor(
    private val destinations: BackupDestinationRunner,
    private val auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<DestinationState> = destinations.state

    /** Label of the chosen device folder; null = none chosen yet. */
    val deviceFolder: StateFlow<String?> = destinations.deviceFolder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val lastBackups: StateFlow<Map<BackupScope, LastBackup>> = destinations.lastBackups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** null until the first emission, so the sheet never flashes a wrong "sign in". */
    val account: StateFlow<AuthState?> = auth.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun backup(target: BackupTarget, scope: BackupScope) = destinations.backup(target, scope)

    fun listDriveBackups() = destinations.listDriveBackups()

    fun restoreFromDrive(backup: StoredBackup) = destinations.restoreFromDrive(backup)

    fun setDeviceFolder(tree: Uri, backupAfter: BackupScope? = null) =
        destinations.setDeviceFolder(tree, backupAfter)

    fun consentLaunched() = destinations.consentLaunched()

    fun consentResult(data: Intent?) = destinations.consentResult(data)

    fun dismiss() = destinations.dismiss()

    /** Drive needs the Google account: sign in first (the chooser needs an Activity), then run [next]. */
    fun signInThen(activity: Context, next: () -> Unit) {
        viewModelScope.launch {
            if (auth.signIn(activity) == SignInResult.SIGNED_IN) next()
        }
    }
}
