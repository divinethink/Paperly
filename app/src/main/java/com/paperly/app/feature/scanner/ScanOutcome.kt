package com.paperly.app.feature.scanner

import com.paperly.app.domain.document.ImportResult

enum class ScanError { Unavailable, Save }

/**
 * [saving] = a save/import is running. [saved] = done, the screen moves on to the Library (or the editor, see
 * [editDocumentId]). [duplicate] = with [saved]: the same scan was already in the Library, nothing new was stored.
 */
data class ScanUiState(
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: ScanError? = null,
    val duplicate: Boolean = false,
    /** Set with [saved] after a gallery import: the new document opens in the page editor. */
    val editDocumentId: String? = null,
    /** Set with [saved] after a camera scan that skips the editor: the new document opens in the Reader. */
    val openDocumentId: String? = null,
)

/** Result of one save: Success and Duplicate both finish (Duplicate says so); anything else is a save error. */
internal fun scanOutcome(result: ImportResult, openEditor: Boolean, openDocument: Boolean = false): ScanUiState =
    when (result) {
        is ImportResult.Success -> ScanUiState(
            saved = true,
            editDocumentId = result.documentId.takeIf { openEditor },
            openDocumentId = result.documentId.takeIf { openDocument && !openEditor },
        )
        is ImportResult.Duplicate -> ScanUiState(saved = true, duplicate = true)
        else -> ScanUiState(error = ScanError.Save)
    }

/**
 * Called when the Scan screen opens: a state left behind by an earlier visit (stuck "saving", old error, an
 * unconsumed "saved") is cleared, unless a save is really still running.
 */
internal fun ScanUiState.onScreenEnter(workRunning: Boolean): ScanUiState = if (workRunning) this else ScanUiState()

/**
 * One scanner start per visit: [take] is true once after [arm]; never while a save runs, and not again after a
 * rotation (the start is already used up while the scanner is open).
 */
class ScanLaunchGate {
    private var armed = true

    fun take(saving: Boolean): Boolean {
        if (!armed || saving) return false
        armed = false
        return true
    }

    /** The scanner closed without a result, or the visit ended: the next visit starts the scanner again. */
    fun arm() {
        armed = true
    }
}
