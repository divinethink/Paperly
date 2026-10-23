package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.backup.BackupScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup export choices, grouped to keep parameter counts small. */
internal class BackupExportUi(
    val chosen: BackupScope,
    val onChoose: (BackupScope) -> Unit,
    /** Backup card "Export": only what the chips selected. */
    val exportChosen: () -> Unit,
    /** "Export my data" / "back up first" before deleting: always everything. */
    val exportAll: () -> Unit,
)

/** One system "save file" picker; remembers which scope the user was exporting when the picker returns. */
@Composable
internal fun rememberBackupExport(viewModel: BackupViewModel): BackupExportUi {
    var chosen by rememberSaveable { mutableStateOf(BackupScope.SCAN) }
    var pending by rememberSaveable { mutableStateOf(BackupScope.SCAN) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { uri -> viewModel.export(uri, pending) }
    }
    return BackupExportUi(
        chosen = chosen,
        onChoose = { chosen = it },
        exportChosen = {
            pending = chosen
            launcher.launch(backupFileName(chosen))
        },
        exportAll = {
            pending = BackupScope.ALL
            launcher.launch(backupFileName(BackupScope.ALL))
        },
    )
}

private fun backupFileName(scope: BackupScope): String {
    val tag = if (scope.fileTag.isEmpty()) "" else scope.fileTag + "-"
    return "paperly-" + tag + "backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"
}

@StringRes
internal fun BackupScope.labelRes(): Int = when (this) {
    BackupScope.ALL -> R.string.backup_scope_all
    BackupScope.SCAN -> R.string.library_name_scanned
    BackupScope.PDF -> R.string.library_name_pdf
    BackupScope.EPUB -> R.string.library_name_epub
}

/** Scanned / PDF / EPUB / All chips with a one-line "N documents · size" summary of the chosen one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupScopeChips(export: BackupExportUi, stats: Map<BackupScope, ScopeStat>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(BackupScope.SCAN, BackupScope.PDF, BackupScope.EPUB, BackupScope.ALL).forEach { scope ->
            FilterChip(
                selected = export.chosen == scope,
                onClick = { export.onChoose(scope) },
                label = { Text(stringResource(scope.labelRes()), maxLines = 1, softWrap = false) },
            )
        }
    }
    val stat = stats[export.chosen] ?: ScopeStat(0, 0L)
    Text(
        pluralStringResource(
            R.plurals.backup_scope_summary,
            stat.count,
            stat.count,
            Formatter.formatShortFileSize(LocalContext.current, stat.bytes),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
