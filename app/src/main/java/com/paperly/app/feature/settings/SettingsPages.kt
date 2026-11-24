package com.paperly.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.core.ui.icons.ArticleGlyph
import com.paperly.app.core.ui.icons.BrightnessGlyph
import com.paperly.app.core.ui.icons.DownloadGlyph
import com.paperly.app.core.ui.icons.FolderGlyph

/** The Settings sections: one row on the hub, one page each. [key] is the navigation argument. */
enum class SettingsPage(
    val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
    val danger: Boolean = false,
) {
    Appearance(
        "appearance",
        R.string.settings_hub_appearance,
        R.string.settings_hub_appearance_summary,
        BrightnessGlyph,
    ),
    Reading("reading", R.string.settings_hub_reading, R.string.settings_hub_reading_summary, ArticleGlyph),
    Storage("storage", R.string.settings_hub_storage, R.string.settings_hub_storage_summary, FolderGlyph),
    Backup("backup", R.string.settings_hub_backup, R.string.settings_hub_backup_summary, DownloadGlyph),
    Privacy("privacy", R.string.settings_hub_privacy, R.string.settings_hub_privacy_summary, Icons.Filled.Lock),
    Danger("danger", R.string.settings_group_danger, R.string.settings_hub_danger_summary, Icons.Filled.Delete, true),
    ;

    companion object {
        val regular: List<SettingsPage> get() = entries.filter { !it.danger }
        fun fromKey(key: String?): SettingsPage? = entries.firstOrNull { it.key == key }
    }
}

/** One Settings page: back-arrow title bar and the section's cards (the same cards, now on their own page). */
@Composable
fun SettingsPageScreen(page: SettingsPage, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PaperlyTopBar(stringResource(page.title), onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
        ) {
            when (page) {
                SettingsPage.Appearance -> AppearancePage()
                SettingsPage.Reading -> ReadingPage()
                SettingsPage.Storage -> StoragePage()
                SettingsPage.Backup -> BackupPage()
                SettingsPage.Privacy -> PrivacyPage()
                SettingsPage.Danger -> DangerPage()
            }
        }
    }
}

@Composable
private fun AppearancePage(appearance: AppearanceViewModel = hiltViewModel()) {
    val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()
    SettingsCard {
        AppThemeRow()
        SettingsDivider()
        AppPaletteRow()
        SettingsDivider()
        ReadingThemeRow()
        SettingsDivider()
        val colorText = SwitchText(
            stringResource(R.string.settings_dynamic_color),
            stringResource(R.string.settings_dynamic_color_summary),
            R.string.settings_dynamic_color_hint,
        )
        SettingSwitchRow(colorText, dynamicColor, appearance::setDynamicColor)
    }
}

@Composable
private fun ReadingPage() {
    SettingsCard {
        PdfFileWriteRow()
        SettingsDivider()
        ScanEditorRow()
        SettingsDivider()
        ScanOcrRow()
    }
}

@Composable
private fun StoragePage(viewModel: SettingsViewModel = hiltViewModel()) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    SettingsCard { StorageCardContent(usage) }
}

@Composable
private fun BackupPage(backupViewModel: BackupViewModel = hiltViewModel()) {
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val stats by backupViewModel.stats.collectAsStateWithLifecycle()
    val export = rememberBackupExport(backupViewModel)
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::previewRestore)
    }
    val checkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(backupViewModel::check)
    }
    backup.confirm?.let { RestoreConfirmDialog(it, backupViewModel::confirmRestore, backupViewModel::cancelRestore) }
    val actions = BackupActions(
        onRestore = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
        onCheck = { checkLauncher.launch(BACKUP_MIME_TYPES) },
    )
    SettingsCard { BackupSection(backup, actions, export, stats) }
    SettingsCard { SyncSection() }
}

@Composable
private fun PrivacyPage(backupViewModel: BackupViewModel = hiltViewModel()) {
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val export = rememberBackupExport(backupViewModel)
    SettingsCard { PrivacySection(busy = backup.busy, onExport = export.exportAll) }
}

@Composable
private fun DangerPage(
    backupViewModel: BackupViewModel = hiltViewModel(),
    deleteViewModel: DeleteDataViewModel = hiltViewModel(),
) {
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val del by deleteViewModel.state.collectAsStateWithLifecycle()
    val export = rememberBackupExport(backupViewModel)
    SettingsCard(danger = true) {
        DeleteDataSection(
            state = del,
            viewModel = deleteViewModel,
            onExport = export.exportAll,
            backupBusy = backup.busy,
        )
    }
}
