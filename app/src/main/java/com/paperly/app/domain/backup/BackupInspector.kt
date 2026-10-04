package com.paperly.app.domain.backup

/** What a backup file contains and what Restore would do with it (nothing here is written anywhere). */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val documents: Int,
    /** Documents Restore would add (not in the Library or Trash yet). */
    val newDocuments: Int,
    val alreadyPresent: Int,
    val folders: Int,
    val bookmarks: Int,
    val annotations: Int,
    val scanPages: Int,
    /** Entries that were unreadable inside the manifest (they would be counted as failed by Restore). */
    val unreadableEntries: Int,
)

sealed interface InspectResult {
    /** The whole file was read and every document matched its SHA-256. */
    data class Ok(val preview: BackupPreview) : InspectResult
    data object Invalid : InspectResult
    data object NewerVersion : InspectResult

    /** [badFiles] = documents whose bytes do not match, or that are missing from the archive. */
    data class Damaged(val badFiles: Int) : InspectResult
    data object Failed : InspectResult
}

/** Read-only "restore drill": reads and verifies a backup without touching the Library, files or database. */
interface BackupInspector {
    /** [sourceUri] = content:// URI of a backup ZIP from the system picker. */
    suspend fun inspect(sourceUri: String): InspectResult
}
