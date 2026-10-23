package com.paperly.app.domain.backup

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface DestinationMessage {
    data class Saved(val target: BackupTarget, val fileName: String, val documents: Int, val skipped: Int) :
        DestinationMessage

    /** The previous backup in that place was not changed. */
    data class SaveFailed(val target: BackupTarget) : DestinationMessage

    data object NeedsSignIn : DestinationMessage

    data object AccessDenied : DestinationMessage

    data object NoDriveBackups : DestinationMessage

    /** Anything unexpected; nothing was changed. */
    data object Failed : DestinationMessage
}

/**
 * [consent] is set while Drive file access still has to be allowed; [driveBackups] while the user is choosing
 * which Drive backup to restore.
 */
data class DestinationState(
    val busy: Boolean = false,
    val message: DestinationMessage? = null,
    val consent: IntentSender? = null,
    val driveBackups: List<StoredBackup>? = null,
)

/**
 * Backup to / restore from a chosen place (Google Drive or a folder on this device). Runs in the app's own scope
 * like [BackupRunner], so leaving the screen never cancels it. One operation at a time.
 */
interface BackupDestinationRunner {
    val state: StateFlow<DestinationState>

    /** Label of the chosen device folder (null = none chosen yet). */
    val deviceFolder: Flow<String?>

    val lastBackups: Flow<Map<BackupScope, LastBackup>>

    fun backup(target: BackupTarget, scope: BackupScope)

    /** Looks up the Drive backups; the result arrives in [DestinationState.driveBackups]. */
    fun listDriveBackups()

    /** Downloads [backup] and hands it to [BackupRunner] as a "verify, then confirm" restore step. */
    fun restoreFromDrive(backup: StoredBackup)

    /** Stores the chosen folder; with [backupAfter] a device backup of that scope starts once it is stored. */
    fun setDeviceFolder(treeUri: Uri, backupAfter: BackupScope? = null)

    fun consentLaunched()

    /** The consent screen has closed; if allowed, the operation that needed it is started again. */
    fun consentResult(data: Intent?)

    /** Clears the message and the backup list (no effect while busy). */
    fun dismiss()
}
