package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.domain.backup.BackupTarget
import com.paperly.app.domain.backup.DestinationMessage
import com.paperly.app.domain.backup.LastBackup
import com.paperly.app.domain.backup.StoredBackup
import com.paperly.app.domain.backup.backupFileName
import java.text.DateFormat
import java.util.Date

private const val ENABLED_ALPHA = 1f
private const val DISABLED_ALPHA = 0.5f

/** Which of the two Backup-card buttons opened the "where?" sheet. */
internal enum class SheetMode { BACKUP, RESTORE }

@StringRes
private fun BackupTarget.nameRes(): Int = when (this) {
    BackupTarget.DRIVE -> R.string.backup_target_name_drive
    BackupTarget.DEVICE -> R.string.backup_target_name_device
}

private fun formatDate(at: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))

/** Backup: tap a place and the ZIP is saved there. Restore: tap a place and pick the backup to restore from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupTargetSheet(
    mode: SheetMode,
    scope: BackupScope,
    viewModel: BackupDestinationViewModel,
    onRestoreFromDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            viewModel.setDeviceFolder(tree, backupAfter = scope.takeIf { mode == SheetMode.BACKUP })
            onDismiss()
        }
    }
    val folder by viewModel.deviceFolder.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            val title = if (mode == SheetMode.BACKUP) R.string.backup_sheet_title else R.string.restore_sheet_title
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            DriveRow(mode, scope, viewModel, onDismiss)
            val deviceHint = when {
                mode == SheetMode.RESTORE -> stringResource(R.string.backup_target_device_restore_hint)
                folder == null -> stringResource(R.string.backup_target_device_pick)
                else -> stringResource(R.string.backup_target_device_folder, folder.orEmpty())
            }
            TargetRow(stringResource(R.string.backup_target_device), deviceHint, true) {
                when {
                    mode == SheetMode.RESTORE -> {
                        onRestoreFromDevice()
                        onDismiss()
                    }
                    folder == null -> pickFolder.launch(null)
                    else -> {
                        viewModel.backup(BackupTarget.DEVICE, scope)
                        onDismiss()
                    }
                }
            }
            if (mode == SheetMode.BACKUP) {
                if (folder != null) {
                    TextButton(onClick = { pickFolder.launch(null) }) {
                        Text(stringResource(R.string.backup_target_change_folder))
                    }
                }
                Text(
                    stringResource(R.string.backup_file_note, scope.backupFileName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_backup_confirm_cancel)) }
        }
    }
}

@Composable
private fun DriveRow(
    mode: SheetMode,
    scope: BackupScope,
    viewModel: BackupDestinationViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val signedIn = account as? AuthState.SignedIn
    val subtitle = when {
        signedIn != null -> stringResource(R.string.backup_target_drive_hint, signedIn.email.orEmpty())
        account == AuthState.Unavailable -> stringResource(R.string.settings_sync_unavailable)
        else -> stringResource(R.string.backup_target_drive_sign_in)
    }
    val enabled = account != null && account != AuthState.Unavailable
    TargetRow(stringResource(R.string.backup_target_drive), subtitle, enabled) {
        val run = {
            if (mode == SheetMode.BACKUP) viewModel.backup(BackupTarget.DRIVE, scope) else viewModel.listDriveBackups()
            onDismiss()
        }
        if (signedIn != null) run() else viewModel.signInThen(context, run)
    }
}

@Composable
private fun TargetRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(if (enabled) ENABLED_ALPHA else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Last backup (PDF): Google Drive · 23 Oct 2026, 10:40" — nothing when this scope was never backed up. */
@Composable
internal fun LastBackupLine(scope: BackupScope, last: Map<BackupScope, LastBackup>) {
    val entry = last[scope] ?: return
    Text(
        stringResource(
            R.string.backup_last,
            stringResource(scope.labelRes()),
            stringResource(entry.target.nameRes()),
            formatDate(entry.at),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Progress text, result message, Drive consent screen and the "which Drive backup?" list of the Backup card. */
@Composable
internal fun BackupDestinationStatus(viewModel: BackupDestinationViewModel) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.consentResult(it.data)
    }
    LaunchedEffect(ui.consent) {
        ui.consent?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
            viewModel.consentLaunched()
        }
    }
    if (ui.busy) Text(stringResource(R.string.settings_backup_working), Modifier.padding(top = 8.dp))
    ui.message?.let { Text(destinationText(it), Modifier.padding(top = 8.dp)) }
    ui.driveBackups?.let { DriveBackupPicker(it, viewModel::restoreFromDrive, viewModel::dismiss) }
}

@Composable
private fun destinationText(m: DestinationMessage): String = when (m) {
    is DestinationMessage.Saved -> stringResource(
        R.string.backup_saved_ok,
        stringResource(m.target.nameRes()),
        m.fileName,
        m.documents,
        m.skipped,
    )
    is DestinationMessage.SaveFailed ->
        stringResource(R.string.backup_save_failed, stringResource(m.target.nameRes()))
    DestinationMessage.NeedsSignIn -> stringResource(R.string.backup_needs_sign_in)
    DestinationMessage.AccessDenied -> stringResource(R.string.backup_access_denied)
    DestinationMessage.NoDriveBackups -> stringResource(R.string.backup_drive_none)
    DestinationMessage.Failed -> stringResource(R.string.backup_failed_generic)
}

@Composable
private fun DriveBackupPicker(backups: List<StoredBackup>, onPick: (StoredBackup) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_drive_pick_title)) },
        text = {
            Column {
                backups.forEach { backup ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(backup) }.padding(vertical = 12.dp)) {
                        Text(backup.name, style = MaterialTheme.typography.bodyLarge)
                        val size = Formatter.formatShortFileSize(context, backup.size)
                        Text(
                            formatDate(backup.modifiedAt) + " · " + size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_backup_confirm_cancel)) }
        },
    )
}
