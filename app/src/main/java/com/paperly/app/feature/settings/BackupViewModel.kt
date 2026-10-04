package com.paperly.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.backup.BackupOutcome
import com.paperly.app.domain.backup.BackupPreview
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunState
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.backup.InspectResult
import com.paperly.app.domain.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface BackupMessage {
    data class ExportOk(val count: Int, val skipped: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data class RestoreOk(val restored: Int, val present: Int, val failed: Int) : BackupMessage
    data object RestoreInvalid : BackupMessage
    data object RestoreNewer : BackupMessage
    data object RestoreFailed : BackupMessage

    /** "Check a backup file" came back healthy; nothing was written. */
    data class CheckOk(val preview: BackupPreview) : BackupMessage
    data class CheckDamaged(val badFiles: Int) : BackupMessage
    data object CheckFailed : BackupMessage
}

/** [confirm] != null = a verified backup is waiting for the user's "Restore" / "Cancel". */
data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val confirm: BackupPreview? = null,
)

/**
 * Thin view of [BackupRunner]: the work lives in the app scope, so it survives leaving this screen.
 * A finished result is shown on return and cleared once the screen is really gone.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(private val runner: BackupRunner) : ViewModel() {
    val state: StateFlow<BackupUiState> = runner.state
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, runner.state.value.toUi())

    fun export(target: Uri) = runner.startExport(target.toString())

    /** Restore step 1: read and verify the file, then ask the user to confirm (see [confirmRestore]). */
    fun previewRestore(source: Uri) = runner.startInspect(source.toString(), forRestore = true)

    /** "Check a backup file": same verification, but never offers to restore. */
    fun check(source: Uri) = runner.startInspect(source.toString(), forRestore = false)

    /** Restore step 2: only valid right after a successful [previewRestore]; otherwise ignored. */
    fun confirmRestore() {
        val inspected = runner.state.value.outcome as? BackupOutcome.Inspected ?: return
        if (inspected.forRestore && inspected.result is InspectResult.Ok) runner.startRestore(inspected.sourceUri)
    }

    fun cancelRestore() = runner.dismissOutcome()

    override fun onCleared() = runner.dismissOutcome()
}

private fun BackupRunState.toUi(): BackupUiState {
    val inspected = outcome as? BackupOutcome.Inspected
    val ok = inspected?.result as? InspectResult.Ok
    return if (inspected != null && inspected.forRestore && ok != null) {
        BackupUiState(busy, confirm = ok.preview)
    } else {
        BackupUiState(busy, outcome?.toMessage())
    }
}

private fun BackupOutcome.toMessage() = when (this) {
    is BackupOutcome.Exported -> result.toMessage()
    is BackupOutcome.Restored -> result.toMessage()
    is BackupOutcome.Inspected -> result.toMessage()
}

private fun InspectResult.toMessage() = when (this) {
    is InspectResult.Ok -> BackupMessage.CheckOk(preview)
    InspectResult.Invalid -> BackupMessage.RestoreInvalid
    InspectResult.NewerVersion -> BackupMessage.RestoreNewer
    is InspectResult.Damaged -> BackupMessage.CheckDamaged(badFiles)
    InspectResult.Failed -> BackupMessage.CheckFailed
}

private fun BackupResult.toMessage() = when (this) {
    is BackupResult.Success -> BackupMessage.ExportOk(documentCount, skipped)
    BackupResult.Failed -> BackupMessage.ExportFailed
}

private fun RestoreResult.toMessage() = when (this) {
    is RestoreResult.Done -> BackupMessage.RestoreOk(summary.restored, summary.alreadyPresent, summary.failed)
    RestoreResult.InvalidBackup -> BackupMessage.RestoreInvalid
    RestoreResult.NewerVersion -> BackupMessage.RestoreNewer
    RestoreResult.Failed -> BackupMessage.RestoreFailed
}
