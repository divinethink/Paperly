package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.privacy.AccountStatus
import com.paperly.app.domain.privacy.CloudWipeBlock
import com.paperly.app.domain.privacy.DeleteOutcome
import com.paperly.app.domain.privacy.DeleteStep

/** Settings > Privacy > "Delete all my data": button, progress, result and the two confirm dialogs. */
@Composable
fun DeleteDataSection(state: DeleteUiState, viewModel: DeleteDataViewModel, onExport: () -> Unit, backupBusy: Boolean) {
    val actions = DeleteDialogActions(
        onNext = viewModel::next,
        onClose = viewModel::close,
        onType = viewModel::type,
        onUnderstand = viewModel::understand,
        onConfirm = viewModel::confirm,
    )
    val onOpen = viewModel::open
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.delete_all_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onOpen,
            enabled = !state.running && !backupBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.delete_all_button)) }
        state.step?.let { Text(stepText(it), Modifier.padding(top = 8.dp)) }
        state.outcome?.let { Text(outcomeText(it), Modifier.padding(top = 8.dp)) }
    }
    when (state.dialog) {
        DeleteDialog.INTRO -> IntroDialog(state, onExport, actions, backupBusy)
        DeleteDialog.FINAL -> FinalDialog(state, actions)
        DeleteDialog.NONE -> Unit
    }
}

private class DeleteDialogActions(
    val onNext: () -> Unit,
    val onClose: () -> Unit,
    val onType: (String) -> Unit,
    val onUnderstand: (Boolean) -> Unit,
    val onConfirm: () -> Unit,
)

@Composable
private fun IntroDialog(state: DeleteUiState, onExport: () -> Unit, actions: DeleteDialogActions, backupBusy: Boolean) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_all_what))
                Text(stringResource(R.string.delete_all_not_covered), Modifier.padding(top = 8.dp))
                if (state.backupSaved) {
                    Text(stringResource(R.string.delete_all_backup_saved), Modifier.padding(top = 8.dp))
                } else {
                    TextButton(onClick = onExport, enabled = !backupBusy) {
                        Text(stringResource(R.string.delete_all_backup_first))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = actions.onNext) { Text(stringResource(R.string.delete_all_continue)) } },
        dismissButton = { TextButton(onClick = actions.onClose) { Text(stringResource(R.string.delete_all_cancel)) } },
    )
}

@Composable
private fun FinalDialog(state: DeleteUiState, actions: DeleteDialogActions) {
    AlertDialog(
        onDismissRequest = actions.onClose,
        title = { Text(stringResource(R.string.delete_all_final_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_all_final_body))
                if (!state.backupSaved) {
                    Row(
                        Modifier.padding(top = 8.dp).toggleable(
                            state.understood,
                            role = Role.Checkbox,
                            onValueChange = actions.onUnderstand,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.understood, onCheckedChange = null)
                        Text(stringResource(R.string.delete_all_no_backup), Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = state.typed,
                    onValueChange = actions.onType,
                    singleLine = true,
                    label = { Text(stringResource(R.string.delete_all_type_label, DELETE_CONFIRM_WORD)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirm, enabled = state.confirmEnabled) {
                Text(stringResource(R.string.delete_all_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = actions.onClose) { Text(stringResource(R.string.delete_all_cancel)) } },
    )
}

@Composable
private fun stepText(step: DeleteStep): String = stringResource(
    when (step) {
        DeleteStep.CLOUD -> R.string.delete_all_step_cloud
        DeleteStep.ACCOUNT -> R.string.delete_all_step_account
        DeleteStep.LOCAL -> R.string.delete_all_step_local
    },
)

@Composable
private fun outcomeText(outcome: DeleteOutcome): String = stringResource(
    when (outcome) {
        is DeleteOutcome.Done -> doneText(outcome)
        is DeleteOutcome.LocalFailed -> R.string.delete_all_local_failed
        is DeleteOutcome.CloudStopped -> when (outcome.reason) {
            CloudWipeBlock.NEEDS_ACCESS -> R.string.delete_all_stopped_access
            CloudWipeBlock.OFFLINE_OR_FAILED -> R.string.delete_all_stopped_offline
            CloudWipeBlock.DENIED -> R.string.delete_all_stopped_denied
            CloudWipeBlock.NOT_EMPTY_AFTER -> R.string.delete_all_stopped_left
        }
    },
)

private fun doneText(done: DeleteOutcome.Done): Int = when {
    !done.cloudWiped -> R.string.delete_all_done_local
    done.account == AccountStatus.KEPT -> R.string.delete_all_done_account_kept
    else -> R.string.delete_all_done_all
}
