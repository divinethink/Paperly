package com.paperly.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.core.ui.icons.ChevronGlyph
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.feature.reader.ReadingTimeViewModel

private const val ICON_TINT_ALPHA = 0.12f
private val HubRowMinHeight = 64.dp
private val HubIconBox = 40.dp
private val HubIconSize = 22.dp

/**
 * Settings hub: today's reading time, then one row per section (Appearance, Reading & scan, Storage,
 * Backup & restore, Privacy) and the Danger zone. Each row opens its own page ([SettingsPageScreen]).
 */
@Composable
fun SettingsScreen(onOpenPage: (SettingsPage) -> Unit, readingTime: ReadingTimeViewModel = hiltViewModel()) {
    val minutesToday by readingTime.todayMinutes.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        PaperlyTopBar(stringResource(R.string.nav_settings))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TodayCard(minutesToday)
            SettingsCard {
                SettingsPage.regular.forEachIndexed { index, page ->
                    if (index > 0) SettingsDivider()
                    HubRow(page) { onOpenPage(page) }
                }
            }
            SettingsCard(danger = true) { HubRow(SettingsPage.Danger) { onOpenPage(SettingsPage.Danger) } }
        }
    }
}

@Composable
private fun TodayCard(minutes: Int) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.large, colors = colors) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_read_today), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_read_today_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                stringResource(R.string.settings_read_today_value, minutes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Icon in a soft tinted circle, title + one-line summary, chevron. Red tint for the Danger zone. */
@Composable
private fun HubRow(page: SettingsPage, onClick: () -> Unit) {
    val tint = if (page.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = HubRowMinHeight).clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(HubIconBox).clip(CircleShape).background(tint.copy(alpha = ICON_TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.icon, contentDescription = null, tint = tint, modifier = Modifier.size(HubIconSize))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(page.title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(page.summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(ChevronGlyph, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal val BACKUP_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

/** Callbacks of the Backup card, grouped to keep parameter counts small. */
internal class BackupActions(val onRestore: () -> Unit, val onCheck: () -> Unit)

@Composable
internal fun BackupSection(
    state: BackupUiState,
    actions: BackupActions,
    export: BackupExportUi,
    stats: Map<BackupScope, ScopeStat>,
    destination: BackupDestinationViewModel = hiltViewModel(),
) {
    var sheet by rememberSaveable { mutableStateOf<SheetMode?>(null) }
    val dest by destination.state.collectAsStateWithLifecycle()
    val last by destination.lastBackups.collectAsStateWithLifecycle()
    val busy = state.busy || dest.busy
    sheet?.let { mode ->
        BackupTargetSheet(mode, export.chosen, destination, actions.onRestore) { sheet = null }
    }
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
            onClick = { sheet = SheetMode.BACKUP },
            enabled = !busy && (stats[export.chosen]?.count ?: 0) > 0,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_export)) }
        FilledTonalButton(
            onClick = { sheet = SheetMode.RESTORE },
            enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_restore)) }
    }
    LastBackupLine(export.chosen, last)
    if (state.busy) Text(stringResource(R.string.settings_backup_working), Modifier.padding(top = 8.dp))
    BackupDestinationStatus(destination)
    state.message?.let { Text(backupMessageText(it), Modifier.padding(top = 8.dp)) }
    SettingsDivider()
    ArrowRow(stringResource(R.string.settings_backup_check), actions.onCheck, enabled = !busy)
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
