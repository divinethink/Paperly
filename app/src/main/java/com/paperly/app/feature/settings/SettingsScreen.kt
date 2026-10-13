package com.paperly.app.feature.settings

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.storage.StorageUsage
import com.paperly.app.feature.reader.ReadingTimeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sections (Reading/Scanner/Sync/...) are added with their phases; Storage arrives in P1. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    readingTime: ReadingTimeViewModel = hiltViewModel(),
    appearance: AppearanceViewModel = hiltViewModel(),
    deleteViewModel: DeleteDataViewModel = hiltViewModel(),
) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val minutesToday by readingTime.todayMinutes.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val del by deleteViewModel.state.collectAsStateWithLifecycle()
    val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let(backupViewModel::export)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::previewRestore)
    }
    val checkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::check)
    }
    backup.confirm?.let { RestoreConfirmDialog(it, backupViewModel::confirmRestore, backupViewModel::cancelRestore) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GroupLabel(R.string.settings_group_preferences)
        SettingsCard {
            CardTitle(R.string.settings_reading_header)
            Text(stringResource(R.string.settings_reading_today, minutesToday), Modifier.padding(bottom = 8.dp))
            DynamicColorRow(dynamicColor, appearance::setDynamicColor)
        }
        GroupLabel(R.string.settings_group_data)
        SettingsCard {
            CardTitle(R.string.settings_storage_header)
            usage?.let { StorageUsageRows(it, context) }
            OrphanFilesSection()
        }
        SettingsCard { SyncSection() }
        SettingsCard {
            BackupSection(
                state = backup.copy(busy = backup.busy || del.running),
                onExport = { exportLauncher.launch(backupFileName()) },
                onRestore = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
                onCheck = { checkLauncher.launch(BACKUP_MIME_TYPES) },
            )
            NewPhoneGuideSection()
        }
        GroupLabel(R.string.settings_group_privacy)
        SettingsCard {
            PrivacySection(busy = backup.busy || del.running, onExport = { exportLauncher.launch(backupFileName()) })
        }
        SettingsCard {
            DeleteDataSection(
                state = del,
                viewModel = deleteViewModel,
                onExport = { exportLauncher.launch(backupFileName()) },
                backupBusy = backup.busy,
            )
        }
    }
}

/** Small section label above a group of cards (Preferences / Data / Privacy). */
@Composable
private fun GroupLabel(@StringRes label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun CardTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** One settings group: a rounded card; the sections inside keep their own behaviour and strings. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Optional Material You colours; the brand palette stays the default. */
@Composable
private fun DynamicColorRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().toggleable(enabled, role = Role.Switch, onValueChange = onChange),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_dynamic_color), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(stringResource(R.string.settings_dynamic_color_hint), style = MaterialTheme.typography.bodySmall)
    }
}

private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

private fun backupFileName(): String =
    "paperly-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"

@Composable
private fun BackupSection(state: BackupUiState, onExport: () -> Unit, onRestore: () -> Unit, onCheck: () -> Unit) {
    Text(
        stringResource(R.string.settings_backup_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExport, enabled = !state.busy) { Text(stringResource(R.string.settings_backup_export)) }
        Button(onClick = onRestore, enabled = !state.busy) { Text(stringResource(R.string.settings_backup_restore)) }
    }
    OutlinedButton(onClick = onCheck, enabled = !state.busy, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_backup_check))
    }
    if (state.busy) Text(stringResource(R.string.settings_backup_working), Modifier.padding(top = 8.dp))
    state.message?.let { Text(backupMessageText(it), Modifier.padding(top = 8.dp)) }
}

@Composable
private fun backupMessageText(m: BackupMessage): String = when (m) {
    is BackupMessage.ExportOk -> stringResource(R.string.settings_backup_export_ok, m.count, m.skipped)
    BackupMessage.ExportFailed -> stringResource(R.string.settings_backup_export_failed)
    is BackupMessage.RestoreOk -> stringResource(R.string.settings_backup_restore_ok, m.restored, m.present, m.failed)
    BackupMessage.RestoreInvalid -> stringResource(R.string.settings_backup_restore_invalid)
    BackupMessage.RestoreNewer -> stringResource(R.string.settings_backup_restore_newer)
    BackupMessage.RestoreFailed -> stringResource(R.string.settings_backup_restore_failed)
    is BackupMessage.CheckOk ->
        stringResource(
            R.string.settings_backup_check_ok,
            m.preview.documents,
            m.preview.newDocuments,
            m.preview.alreadyPresent,
        )
    is BackupMessage.CheckDamaged -> stringResource(R.string.settings_backup_check_damaged, m.badFiles)
    BackupMessage.CheckFailed -> stringResource(R.string.settings_backup_check_failed)
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StorageUsageRows(u: StorageUsage, context: Context) {
    UsageRow(
        pluralStringResource(R.plurals.settings_storage_documents, u.activeCount, u.activeCount),
        Formatter.formatShortFileSize(context, u.activeBytes),
    )
    UsageRow(
        pluralStringResource(R.plurals.settings_storage_trash, u.trashCount, u.trashCount),
        Formatter.formatShortFileSize(context, u.trashBytes),
    )
    UsageRow(
        stringResource(R.string.settings_storage_total),
        Formatter.formatShortFileSize(context, u.totalBytes),
    )
}
