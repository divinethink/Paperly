package com.paperly.app.domain.backup

import android.content.Intent
import android.content.IntentSender
import java.io.File

/** Where a backup ZIP is kept. */
enum class BackupTarget { DRIVE, DEVICE }

/** One fixed file name per scope: saving again replaces the previous backup instead of piling up copies. */
fun BackupScope.backupFileName(): String = when (this) {
    BackupScope.ALL -> "Paperly-All.zip"
    BackupScope.SCAN -> "Paperly-Scan.zip"
    BackupScope.PDF -> "Paperly-PDF.zip"
    BackupScope.EPUB -> "Paperly-EPUB.zip"
}

/** A backup ZIP found in the user's Drive folder "Paperly Backup". */
data class StoredBackup(val id: String, val name: String, val size: Long, val modifiedAt: Long)

/** The last successful backup of one scope (kept on this phone only, to show "Last backup: ..."). */
data class LastBackup(val target: BackupTarget, val at: Long)

sealed interface DriveResult<out T> {
    data class Ok<T>(val value: T) : DriveResult<T>

    /** The user has not allowed Drive file access yet: launch [request] from a screen, then try again. */
    data class NeedsConsent(val request: IntentSender) : DriveResult<Nothing>

    data object NotSignedIn : DriveResult<Nothing>

    data object Failed : DriveResult<Nothing>
}

/**
 * The visible Drive folder "Paperly Backup" (scope drive.file: Paperly only sees files it created itself).
 * Every call is one self-contained operation; nothing is stored here.
 */
interface DriveBackupStore {
    /** Only checks that Drive can be used right now (signed in and allowed). */
    suspend fun access(): DriveResult<Unit>

    /**
     * Uploads [file] as [name] into the folder. The new copy is checked against the local file first; only then
     * are older copies with the same name removed, so a failed upload never costs the previous backup.
     */
    suspend fun upload(file: File, name: String): DriveResult<Unit>

    /** Paperly backups in the folder, newest first (empty when there is no folder yet). */
    suspend fun list(): DriveResult<List<StoredBackup>>

    suspend fun download(id: String, target: File): DriveResult<Unit>

    /** The consent screen has closed: true when file access is allowed now. */
    suspend fun consentGranted(data: Intent?): Boolean
}
