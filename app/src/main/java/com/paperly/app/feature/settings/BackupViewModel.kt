package com.paperly.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.backup.BackupRepository
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.RestoreResult
import com.paperly.app.domain.sync.SyncQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupMessage {
    data class ExportOk(val count: Int, val skipped: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data class RestoreOk(val restored: Int, val present: Int, val failed: Int) : BackupMessage
    data object RestoreInvalid : BackupMessage
    data object RestoreNewer : BackupMessage
    data object RestoreFailed : BackupMessage
}

data class BackupUiState(val busy: Boolean = false, val message: BackupMessage? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val syncQueue: SyncQueue,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(target: Uri) = launchOp { repository.export(target.toString()).toMessage() }

    fun restore(source: Uri) = launchOp {
        val result = repository.restore(source.toString())
        if (result is RestoreResult.Done && result.summary.restored > 0) syncQueue.allDocumentsChanged()
        result.toMessage()
    }

    /** Ignored while busy: a double-tap can never start a second operation. */
    private fun launchOp(block: suspend () -> BackupMessage) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch { _state.value = BackupUiState(message = block()) }
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
}
