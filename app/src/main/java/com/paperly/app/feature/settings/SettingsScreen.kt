package com.paperly.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.feature.reader.ReadingTimeViewModel

/** Settings: Reading, Data (storage, sync, backup), Privacy and the Danger zone. */
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
    val stats by backupViewModel.stats.collectAsStateWithLifecycle()
    val export = rememberBackupExport(backupViewModel)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::previewRestore)
    }
    val checkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::check)
    }
    backup.confirm?.let { RestoreConfirmDialog(it, backupViewModel::confirmRestore, backupViewModel::cancelRestore) }
    val onExport = export.exportAll
    val busy = backup.busy || del.running
    val actions = BackupActions(
        onExport = export.exportChosen,
        onRestore = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
        onCheck = { checkLauncher.launch(BACKUP_MIME_TYPES) },
    )
    Column(Modifier.fillMaxWidth()) {
        PaperlyTopBar(stringResource(R.string.nav_settings))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            GroupLabel(R.string.settings_group_preferences)
            ReadingCard(minutesToday, dynamicColor, appearance::setDynamicColor)
            GroupLabel(R.string.settings_group_data)
            SettingsCard { StorageCardContent(usage) }
            SettingsCard { SyncSection() }
            SettingsCard { BackupSection(backup.copy(busy = busy), actions, export, stats) }
            GroupLabel(R.string.settings_group_privacy)
            SettingsCard { PrivacySection(busy = busy, onExport = onExport) }
            GroupLabel(R.string.settings_group_danger, danger = true)
            SettingsCard(danger = true) {
                DeleteDataSection(
                    state = del,
                    viewModel = deleteViewModel,
                    onExport = onExport,
                    backupBusy = backup.busy,
                )
            }
        }
    }
}

@Composable
private fun ReadingCard(minutesToday: Int, dynamicColor: Boolean, onDynamicColor: (Boolean) -> Unit) {
    SettingsCard {
        ValueRow(
            stringResource(R.string.settings_read_today),
            stringResource(R.string.settings_read_today_summary),
            stringResource(R.string.settings_read_today_value, minutesToday),
        )
        SettingsDivider()
        val colorText = SwitchText(
            stringResource(R.string.settings_dynamic_color),
            stringResource(R.string.settings_dynamic_color_summary),
            R.string.settings_dynamic_color_hint,
        )
        SettingSwitchRow(colorText, dynamicColor, onDynamicColor)
        SettingsDivider()
        PdfFileWriteRow()
        SettingsDivider()
        ScanEditorRow()
        SettingsDivider()
        ScanOcrRow()
    }
}

private val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

/** Callbacks of the Backup card, grouped to keep parameter counts small. */
private class BackupActions(val onExport: () -> Unit, val onRestore: () -> Unit, val onCheck: () -> Unit)

@Composable
private fun BackupSection(
    state: BackupUiState,
    actions: BackupActions,
    export: BackupExportUi,
    stats: Map<BackupScope, ScopeStat>,
) {
    CardHeader(R.string.settings_backup_header)
    Text(
        stringResource(R.string.settings_backup_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    BackupScopeChips(export, stats)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = actions.onExport,
            enabled = !state.busy && (stats[export.chosen]?.count ?: 0) > 0,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_export)) }
        FilledTonalButton(
            onClick = actions.onRestore,
            enabled = !state.busy,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_restore)) }
    }
    if (state.busy) Text(stringResource(R.string.settings_backup_working), Modifier.padding(top = 8.dp))
    state.message?.let { Text(backupMessageText(it), Modifier.padding(top = 8.dp)) }
    SettingsDivider()
    ArrowRow(stringResource(R.string.settings_backup_check), actions.onCheck, enabled = !state.busy)
    SettingsDivider()
    NewPhoneGuideSection()
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
