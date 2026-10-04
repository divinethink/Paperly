package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.paperly.app.R
import com.paperly.app.domain.backup.BackupPreview
import java.text.DateFormat
import java.util.Date

/** Restore step 2: what the (already fully verified) backup holds and what would be added. Nothing is written yet. */
@Composable
fun RestoreConfirmDialog(preview: BackupPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_backup_confirm_title)) },
        text = {
            Column {
                val made = if (preview.createdAt > 0) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(preview.createdAt))
                } else {
                    stringResource(R.string.settings_backup_date_unknown)
                }
                Text(stringResource(R.string.settings_backup_confirm_made, made))
                Text(
                    stringResource(
                        R.string.settings_backup_confirm_documents,
                        preview.documents,
                        preview.newDocuments,
                        preview.alreadyPresent,
                    ),
                )
                Text(
                    stringResource(
                        R.string.settings_backup_confirm_extras,
                        preview.folders,
                        preview.bookmarks,
                        preview.annotations,
                        preview.scanPages,
                    ),
                )
                if (preview.unreadableEntries > 0) {
                    Text(stringResource(R.string.settings_backup_confirm_unreadable, preview.unreadableEntries))
                }
                Text(stringResource(R.string.settings_backup_confirm_safe))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_backup_confirm_restore)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_backup_confirm_cancel)) } },
    )
}
