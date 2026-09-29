package com.paperly.app.domain.backup

sealed interface BackupResult {
    /** [skipped] = documents whose file was missing on disk (not included in the archive). */
    data class Success(val documentCount: Int, val skipped: Int) : BackupResult
    data object Failed : BackupResult
}

data class RestoreSummary(val restored: Int, val alreadyPresent: Int, val failed: Int)

sealed interface RestoreResult {
    data class Done(val summary: RestoreSummary) : RestoreResult
    data object InvalidBackup : RestoreResult

    /** Backup was made by a newer app version than this one understands. */
    data object NewerVersion : RestoreResult
    data object Failed : RestoreResult
}

/**
 * Export = one ZIP (documents + manifest), verified after writing.
 * Restore = additive merge: never overwrites or deletes existing data (Zero Data Loss).
 */
interface BackupRepository {
    /** [targetUri] = content:// URI from the system "create document" picker. */
    suspend fun export(targetUri: String): BackupResult

    /** [sourceUri] = content:// URI of a backup ZIP from the system picker. */
    suspend fun restore(sourceUri: String): RestoreResult
}
