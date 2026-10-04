package com.paperly.app.domain.backup

import kotlinx.coroutines.flow.StateFlow

/** What the last finished Export/Restore returned. Kept until the next start or [BackupRunner.dismissOutcome]. */
sealed interface BackupOutcome {
    data class Exported(val result: BackupResult) : BackupOutcome
    data class Restored(val result: RestoreResult) : BackupOutcome

    /**
     * Read-only look at [sourceUri]. [forRestore] = the user is about to restore it: show [InspectResult.Ok] as a
     * confirm step; otherwise it was a plain "check this file" and the result is the whole answer.
     */
    data class Inspected(val sourceUri: String, val result: InspectResult, val forRestore: Boolean) : BackupOutcome
}

data class BackupRunState(val busy: Boolean = false, val outcome: BackupOutcome? = null)

/**
 * Runs Export/Restore in the app's own scope, not in a screen's: leaving Settings, rotating or backgrounding the
 * app never cancels a half-written archive or a half-done restore, and the result is still there on return.
 * At most one operation at a time; a second start while busy is ignored (double-tap / retry safe).
 * Not a guarantee against process death (the OS can still kill the app): an interrupted export has no manifest
 * and is rejected by Restore; an interrupted restore is safe to repeat (additive, skips what is present).
 */
interface BackupRunner {
    val state: StateFlow<BackupRunState>

    /** [targetUri] = content:// URI from the system "create document" picker. */
    fun startExport(targetUri: String)

    /** [sourceUri] = content:// URI of a backup ZIP from the system picker. */
    fun startRestore(sourceUri: String)

    /** Verify a backup without writing anything (see [BackupInspector]). */
    fun startInspect(sourceUri: String, forRestore: Boolean)

    /** Clears a finished result. No effect while an operation is running. */
    fun dismissOutcome()
}
