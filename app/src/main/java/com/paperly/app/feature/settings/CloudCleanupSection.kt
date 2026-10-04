package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.sync.CleanupBlock

/** Manual clean-up of unused files in the user's hidden Drive app folder. Never runs by itself. */
@Composable
fun CloudCleanupRow(viewModel: CloudCleanupViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.settings_cleanup_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = viewModel::check,
            enabled = ui !is CleanupUi.Working,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.settings_cleanup_button)) }
        cleanupMessage(ui)?.let {
            Text(it, Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    (ui as? CleanupUi.Confirm)?.let { ConfirmDialog(it, viewModel::confirm, viewModel::dismiss) }
}

@Composable
private fun ConfirmDialog(confirm: CleanupUi.Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val size = Formatter.formatShortFileSize(LocalContext.current, confirm.bytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cleanup_confirm_title)) },
        text = { Text(stringResource(R.string.settings_cleanup_confirm_body, confirm.count, size)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_cleanup_confirm_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cleanup_cancel)) } },
    )
}

@Composable
private fun cleanupMessage(ui: CleanupUi): String? = when (ui) {
    CleanupUi.Idle, is CleanupUi.Confirm -> null
    CleanupUi.Working -> stringResource(R.string.settings_cleanup_checking)
    CleanupUi.NothingToClean -> stringResource(R.string.settings_cleanup_nothing)
    is CleanupUi.Blocked -> stringResource(
        when (ui.reason) {
            CleanupBlock.PAUSED -> R.string.settings_cleanup_blocked_paused
            CleanupBlock.SYNC_BUSY -> R.string.settings_cleanup_blocked_busy
            CleanupBlock.NEEDS_ACCESS -> R.string.settings_cleanup_blocked_access
            CleanupBlock.NOT_SIGNED_IN, CleanupBlock.UNAVAILABLE -> R.string.settings_cleanup_blocked_unavailable
        },
    )
    is CleanupUi.Done ->
        if (ui.remaining == 0) {
            stringResource(R.string.settings_cleanup_done_all, ui.deleted)
        } else {
            stringResource(R.string.settings_cleanup_done_some, ui.deleted, ui.remaining)
        }
}
