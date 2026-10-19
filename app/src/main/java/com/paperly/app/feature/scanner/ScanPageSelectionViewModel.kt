package com.paperly.app.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.scanner.ScanEditRepository
import com.paperly.app.domain.scanner.ScanEditResult
import com.paperly.app.domain.scanner.ScanEditorSettings
import com.paperly.app.domain.scanner.ScanOutputSettings
import com.paperly.app.domain.scanner.canDeleteAll
import com.paperly.app.domain.scanner.withoutAll
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why "Delete" did nothing; the screen shows a short message, then calls `dismissNotice`. */
enum class PagesNotice { NOT_EDITABLE, BLOCKED, FAILED }

/**
 * Pages ticked in the grid (0-based) and the multi-page delete. Delete uses the same safe rebuild as the page
 * editor ([ScanEditRepository.apply]: temp file, verify, backup, atomic replace), so a failure leaves the document
 * as it was.
 */
@HiltViewModel
class ScanPageSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ScanEditRepository,
    private val editorSettings: ScanEditorSettings,
    private val outputSettings: ScanOutputSettings,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    private val _busy = MutableStateFlow(false)
    private val _notice = MutableStateFlow<PagesNotice?>(null)

    /** Ticked pages; selection mode is on while this is not empty. */
    val selected: StateFlow<Set<Int>> = _selected.asStateFlow()

    /** A delete is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val notice: StateFlow<PagesNotice?> = _notice.asStateFlow()

    fun toggle(index: Int) = _selected.update { if (index in it) it - index else it + index }

    fun select(index: Int) = _selected.update { it + index }

    fun selectAll(pageCount: Int) {
        _selected.value = (0 until pageCount).toSet()
    }

    fun clear() {
        if (!_busy.value) _selected.value = emptySet()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Deletes the ticked pages, then [onDone] (the grid reads the document again). */
    fun delete(onDone: () -> Unit) {
        val chosen = _selected.value
        if (chosen.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val problem = try {
                tryDelete(chosen)
            } finally {
                _busy.value = false
            }
            if (problem == null) {
                _selected.value = emptySet()
                onDone()
            } else {
                _notice.value = problem
            }
        }
    }

    /** null = deleted; otherwise the reason. Nothing is written unless every check passed. */
    private suspend fun tryDelete(chosen: Set<Int>): PagesNotice? {
        val stored = if (editorSettings.enabled.first()) repository.load(documentId) else null
        return when {
            stored == null -> PagesNotice.NOT_EDITABLE
            !stored.canDeleteAll(chosen) -> PagesNotice.BLOCKED
            repository.apply(documentId, stored.withoutAll(chosen), outputSettings.size.first()) ==
                ScanEditResult.SAVED -> null
            else -> PagesNotice.FAILED
        }
    }
}
