package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.backup.BackupOutcome
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.privacy.DeleteAllDataRunner
import com.paperly.app.domain.privacy.DeleteOutcome
import com.paperly.app.domain.privacy.DeleteStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class DeleteDialog { NONE, INTRO, FINAL }

/** The word the user must type to arm the final button (case-insensitive). */
const val DELETE_CONFIRM_WORD = "DELETE"

private data class DeleteForm(
    val dialog: DeleteDialog = DeleteDialog.NONE,
    val typed: String = "",
    val understood: Boolean = false,
)

data class DeleteUiState(
    val dialog: DeleteDialog = DeleteDialog.NONE,
    val typed: String = "",
    val understood: Boolean = false,
    /** A backup was saved and verified in this session (and not dismissed since). */
    val backupSaved: Boolean = false,
    val step: DeleteStep? = null,
    val outcome: DeleteOutcome? = null,
) {
    val running: Boolean get() = step != null

    /** Armed = word typed, and (no backup -> separately acknowledged). Never while running. */
    val confirmEnabled: Boolean
        get() = !running && typed.trim().equals(DELETE_CONFIRM_WORD, ignoreCase = true) && (backupSaved || understood)
}

/** Thin view of [DeleteAllDataRunner]; two dialogs (what / type DELETE) guard the one irreversible action. */
@HiltViewModel
class DeleteDataViewModel @Inject constructor(
    private val runner: DeleteAllDataRunner,
    backup: BackupRunner,
) : ViewModel() {
    private val form = MutableStateFlow(DeleteForm())

    val state: StateFlow<DeleteUiState> = combine(form, runner.state, backup.state) { f, run, b ->
        val saved = (b.outcome as? BackupOutcome.Exported)?.result is BackupResult.Success
        DeleteUiState(f.dialog, f.typed, f.understood, saved, run.step, run.outcome)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeleteUiState())

    fun open() {
        runner.dismissOutcome()
        form.value = DeleteForm(DeleteDialog.INTRO)
    }

    fun next() {
        form.value = DeleteForm(DeleteDialog.FINAL)
    }

    fun close() {
        form.value = DeleteForm()
    }

    fun type(text: String) {
        form.value = form.value.copy(typed = text.take(MAX_TYPED))
    }

    fun understand(value: Boolean) {
        form.value = form.value.copy(understood = value)
    }

    fun confirm() {
        if (!state.value.confirmEnabled || state.value.dialog != DeleteDialog.FINAL) return
        close()
        runner.start()
    }

    fun dismissOutcome() = runner.dismissOutcome()

    private companion object {
        const val MAX_TYPED = 20
    }
}
