package com.paperly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.backup.BackupOutcome
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.privacy.DeleteAllDataRunner
import com.paperly.app.domain.privacy.DeleteOutcome
import com.paperly.app.domain.privacy.DeleteScope
import com.paperly.app.domain.privacy.DeleteStep
import com.paperly.app.domain.privacy.ScopedDeleteOutcome
import com.paperly.app.domain.privacy.ScopedDeleteRunner
import com.paperly.app.domain.trash.TrashRepository
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

private const val STATS_TIMEOUT_MS = 5_000L

private data class DeleteForm(
    val scope: DeleteScope = DeleteScope.TRASH,
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
    val scope: DeleteScope = DeleteScope.TRASH,
    val scopedRunning: Boolean = false,
    val scopedOutcome: ScopedDeleteOutcome? = null,
    val stats: Map<DeleteScope, ScopeStat> = emptyMap(),
) {
    val running: Boolean get() = step != null || scopedRunning

    /** Nothing to delete in the chosen field (ALL is always allowed: it also clears the cloud and settings). */
    val scopeEmpty: Boolean get() = scope != DeleteScope.ALL && (stats[scope]?.count ?: 0) == 0

    /** Armed = word typed, and (no backup -> separately acknowledged). Never while running. */
    val confirmEnabled: Boolean
        get() = !running && typed.trim().equals(DELETE_CONFIRM_WORD, ignoreCase = true) && (backupSaved || understood)
}

/** Thin view of [DeleteAllDataRunner]; two dialogs (what / type DELETE) guard the one irreversible action. */
@HiltViewModel
class DeleteDataViewModel @Inject constructor(
    private val runner: DeleteAllDataRunner,
    private val scoped: ScopedDeleteRunner,
    backup: BackupRunner,
    documents: DocumentRepository,
    trash: TrashRepository,
) : ViewModel() {
    private val form = MutableStateFlow(DeleteForm())

    /** Live counts for the Trash / Scanned / PDF / EPUB / All chips (All = live documents + Trash). */
    private val stats: StateFlow<Map<DeleteScope, ScopeStat>> =
        combine(documents.observeDocuments(), trash.observeTrash()) { live, trashed ->
            DeleteScope.entries.associateWith { scope ->
                val docs = when (scope) {
                    DeleteScope.TRASH -> trashed
                    DeleteScope.ALL -> live + trashed
                    else -> live.filter { it.type == scope.type?.docType }
                }
                ScopeStat(docs.size, docs.sumOf { it.sizeBytes })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATS_TIMEOUT_MS), emptyMap())

    val state: StateFlow<DeleteUiState> =
        combine(form, runner.state, backup.state, scoped.state, stats) { f, run, b, sc, st ->
            val saved = (b.outcome as? BackupOutcome.Exported)?.result is BackupResult.Success
            DeleteUiState(
                f.dialog, f.typed, f.understood, saved, run.step, run.outcome,
                f.scope, sc.running, sc.outcome, st,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DeleteUiState())

    fun choose(scope: DeleteScope) {
        if (!state.value.running) form.value = DeleteForm(scope = scope)
    }

    fun open() {
        runner.dismissOutcome()
        scoped.dismissOutcome()
        form.value = DeleteForm(DeleteDialog.INTRO, scope = form.value.scope)
    }

    fun next() {
        form.value = DeleteForm(DeleteDialog.FINAL, scope = form.value.scope)
    }

    fun close() {
        form.value = DeleteForm(scope = form.value.scope)
    }

    fun type(text: String) {
        form.value = form.value.copy(typed = text.take(MAX_TYPED))
    }

    fun understand(value: Boolean) {
        form.value = form.value.copy(understood = value)
    }

    fun confirm() {
        if (!state.value.confirmEnabled || state.value.dialog != DeleteDialog.FINAL) return
        val scope = state.value.scope
        close()
        if (scope == DeleteScope.ALL) runner.start() else scoped.start(scope)
    }

    fun dismissOutcome() {
        runner.dismissOutcome()
        scoped.dismissOutcome()
    }

    private companion object {
        const val MAX_TYPED = 20
    }
}
