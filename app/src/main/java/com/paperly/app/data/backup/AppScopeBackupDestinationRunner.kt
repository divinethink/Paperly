package com.paperly.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.backup.BackupDestinationRunner
import com.paperly.app.domain.backup.BackupRepository
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.domain.backup.BackupTarget
import com.paperly.app.domain.backup.DestinationMessage
import com.paperly.app.domain.backup.DestinationState
import com.paperly.app.domain.backup.DriveBackupStore
import com.paperly.app.domain.backup.DriveResult
import com.paperly.app.domain.backup.LastBackup
import com.paperly.app.domain.backup.StoredBackup
import com.paperly.app.domain.backup.backupFileName
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TEMP_DIR = "backup-tmp"
private const val DRIVE_RESTORE_FILE = "drive-restore.zip"

/**
 * Builds the ZIP with the existing [BackupRepository] (into a temporary file, so the verified archive code is
 * reused unchanged) and then puts it in the chosen place under its fixed name. The old backup is only replaced
 * after the new one has been checked there. Restore from Drive downloads the ZIP and hands it to [BackupRunner],
 * whose "verify, then confirm" step and additive restore are unchanged.
 */
@Singleton
class AppScopeBackupDestinationRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val restoreRunner: BackupRunner,
    private val drive: DriveBackupStore,
    private val device: DeviceBackupStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : BackupDestinationRunner {
    private val _state = MutableStateFlow(DestinationState())
    override val state: StateFlow<DestinationState> = _state.asStateFlow()
    override val deviceFolder: Flow<String?> = device.folderLabel
    override val lastBackups: Flow<Map<BackupScope, LastBackup>> = device.lastBackups

    /** What to run again once the user has allowed Drive access. */
    @Volatile
    private var pending: (() -> Unit)? = null

    override fun backup(target: BackupTarget, scope: BackupScope): Unit = start {
        val name = scope.backupFileName()
        val access = if (target == BackupTarget.DRIVE) drive.access() else DriveResult.Ok(Unit)
        val retry = { backup(target, scope) }
        if (access !is DriveResult.Ok) return@start problem(access, retry, DestinationMessage.SaveFailed(target))
        val temp = tempFile(name).also { it.delete() }
        try {
            val exported = repository.export(Uri.fromFile(temp).toString(), scope)
            val saved: DriveResult<Unit> = when {
                exported !is BackupResult.Success -> DriveResult.Failed
                target == BackupTarget.DRIVE -> drive.upload(temp, name)
                device.save(temp, name) -> DriveResult.Ok(Unit)
                else -> DriveResult.Failed
            }
            if (saved is DriveResult.Ok && exported is BackupResult.Success) {
                device.recordBackup(scope, LastBackup(target, System.currentTimeMillis()))
                DestinationState(
                    message = DestinationMessage.Saved(target, name, exported.documentCount, exported.skipped),
                )
            } else {
                problem(saved, retry, DestinationMessage.SaveFailed(target))
            }
        } finally {
            temp.delete()
        }
    }

    override fun listDriveBackups(): Unit = start {
        when (val found = drive.list()) {
            is DriveResult.Ok ->
                if (found.value.isEmpty()) {
                    DestinationState(message = DestinationMessage.NoDriveBackups)
                } else {
                    DestinationState(driveBackups = found.value)
                }
            else -> problem(found, ::listDriveBackups, DestinationMessage.Failed)
        }
    }

    override fun restoreFromDrive(backup: StoredBackup): Unit = start {
        val file = tempFile(DRIVE_RESTORE_FILE).also { it.delete() }
        val downloaded = drive.download(backup.id, file)
        if (downloaded is DriveResult.Ok) {
            restoreRunner.startInspect(Uri.fromFile(file).toString(), forRestore = true)
            DestinationState()
        } else {
            file.delete()
            problem(downloaded, { restoreFromDrive(backup) }, DestinationMessage.Failed)
        }
    }

    override fun setDeviceFolder(treeUri: Uri, backupAfter: BackupScope?) {
        appScope.launch {
            device.setFolder(treeUri)
            backupAfter?.let { backup(BackupTarget.DEVICE, it) }
        }
    }

    override fun consentLaunched() = _state.update { it.copy(consent = null) }

    override fun consentResult(data: Intent?) {
        appScope.launch {
            val retry = pending
            pending = null
            if (drive.consentGranted(data)) {
                retry?.invoke()
            } else {
                _state.value = DestinationState(message = DestinationMessage.AccessDenied)
            }
        }
    }

    override fun dismiss() = _state.update { if (it.busy) it else DestinationState() }

    /** Only one caller can flip idle -> busy (atomic), so a double-tap never starts a second operation. */
    private fun start(block: suspend () -> DestinationState) {
        while (true) {
            val current = _state.value
            if (current.busy) return
            if (_state.compareAndSet(current, DestinationState(busy = true))) break
        }
        appScope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                _state.value = DestinationState() // busy can never get stuck
                throw e
            } catch (e: Exception) {
                DestinationState(message = DestinationMessage.Failed)
            }
            _state.value = result
        }
    }

    /** Turns a Drive answer that is not "done" into the next screen state; remembers [retry] for the consent case. */
    private fun problem(result: DriveResult<*>, retry: () -> Unit, failed: DestinationMessage): DestinationState =
        when (result) {
            is DriveResult.NeedsConsent -> {
                pending = retry
                DestinationState(consent = result.request)
            }
            DriveResult.NotSignedIn -> DestinationState(message = DestinationMessage.NeedsSignIn)
            else -> DestinationState(message = failed)
        }

    private fun tempFile(name: String): File = File(File(context.cacheDir, TEMP_DIR).apply { mkdirs() }, name)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupDestinationModule {
    @Binds
    abstract fun bindBackupDestinationRunner(impl: AppScopeBackupDestinationRunner): BackupDestinationRunner
}
