package com.paperly.app.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.canDeleteAll
import com.paperly.app.domain.scanner.picked
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TransferMode { MOVE, COPY }

/** What the owner is told when a Move/Copy ends (one short message). */
enum class TransferNotice {
    COPIED,
    MOVED,
    NOT_EDITABLE,
    MOVE_BLOCKED,
    DUPLICATE,
    FAILED,
    PARTIAL_COPY,
    PARTIAL_MOVE,
    NOT_REMOVED,
}

/** Result of the copy step -> message. A Move that copied fine still has to remove the originals (see the model). */
internal fun TransferResult.toNotice(mode: TransferMode): TransferNotice = when (this) {
    TransferResult.DONE -> if (mode == TransferMode.MOVE) TransferNotice.MOVED else TransferNotice.COPIED
    TransferResult.PARTIAL ->
        if (mode == TransferMode.MOVE) TransferNotice.PARTIAL_MOVE else TransferNotice.PARTIAL_COPY
    TransferResult.NOT_EDITABLE -> TransferNotice.NOT_EDITABLE
    TransferResult.DUPLICATE -> TransferNotice.DUPLICATE
    TransferResult.FAILED -> TransferNotice.FAILED
}

/**
 * Move/Copy of the ticked pages (0-based positions, passed in by the screen). Move = Copy first; only when the copy
 * is verified are the originals removed, so a failure in between leaves the pages in the source (never lost).
 */
@HiltViewModel
class ScanPageTransferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transfer: ScanPageTransfer,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>("documentId").orEmpty()
    private val _targets = MutableStateFlow<List<TransferTarget>?>(null)
    private val _busy = MutableStateFlow(false)
    private val _notice = MutableStateFlow<TransferNotice?>(null)

    /** Rows of the picker; null while they load. */
    val targets: StateFlow<List<TransferTarget>?> = _targets.asStateFlow()

    /** A move/copy is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    val notice: StateFlow<TransferNotice?> = _notice.asStateFlow()

    fun loadTargets(mode: TransferMode) {
        _targets.value = null
        viewModelScope.launch { _targets.value = transfer.targets(documentId, forMove = mode == TransferMode.MOVE) }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** [targetId] null = a new document. [onDone] runs after a full success (`moved` = the source changed). */
    fun start(mode: TransferMode, positions: Set<Int>, targetId: String?, onDone: (moved: Boolean) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val notice = try {
                execute(mode, positions, targetId)
            } finally {
                _busy.value = false
            }
            _notice.value = notice
            if (notice == TransferNotice.COPIED || notice == TransferNotice.MOVED) {
                onDone(notice == TransferNotice.MOVED)
            }
        }
    }

    /** Checks first (a Move that could not remove its originals does not even copy), then copies, then removes. */
    private suspend fun execute(mode: TransferMode, positions: Set<Int>, targetId: String?): TransferNotice {
        val stored = transfer.pagesOf(documentId)
        val picks = stored?.picked(positions)
        return when {
            stored == null || picks == null -> TransferNotice.NOT_EDITABLE
            mode == TransferMode.MOVE && !stored.canDeleteAll(positions) -> TransferNotice.MOVE_BLOCKED
            else -> copyThenRemove(mode, positions, picks, targetId)
        }
    }

    private suspend fun copyThenRemove(
        mode: TransferMode,
        positions: Set<Int>,
        picks: List<ScanEditPage>,
        targetId: String?,
    ): TransferNotice {
        val notice = transfer.copy(documentId, picks, targetId).toNotice(mode)
        val removed = notice != TransferNotice.MOVED || transfer.remove(documentId, positions)
        return if (removed) notice else TransferNotice.NOT_REMOVED
    }
}
