package com.paperly.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.SyncQueueCounts

/** Settings -> Sync. Sign-in is optional; nothing else in the app depends on it. */
@Composable
fun SyncSection(viewModel: SyncAccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        SyncHeader(expanded) { expanded = !expanded }
        if (expanded) SyncBody(account, ui, viewModel)
    }
}

/** Tap the title to open or close the card (closed by default). */
@Composable
private fun SyncHeader(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_sync_header),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        val label = if (expanded) R.string.settings_sync_collapse else R.string.settings_sync_expand
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(label),
        )
    }
}

@Composable
private fun SyncBody(account: AuthState?, ui: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_sync_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (account) {
        null -> Unit
        AuthState.Unavailable ->
            Text(stringResource(R.string.settings_sync_unavailable), Modifier.padding(top = 8.dp))
        AuthState.SignedOut -> Button(
            onClick = { viewModel.signIn(context) },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_sync_sign_in)) }
        is AuthState.SignedIn -> SignedInRows(account, ui, viewModel)
    }
    signInMessage(ui.result)?.let { Text(it, Modifier.padding(top = 8.dp)) }
    ui.detail?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SignedInRows(account: AuthState.SignedIn, ui: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    val context = LocalContext.current
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val queue by viewModel.queueCounts.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.data)
    }
    LaunchedEffect(account.uid) { viewModel.refreshAccess() }
    val name = account.email ?: account.uid
    Text(stringResource(R.string.settings_sync_signed_in_as, name), Modifier.padding(top = 8.dp))
    SyncSwitchRow(stringResource(R.string.settings_sync_continue_switch), !paused) { viewModel.setPaused(!it) }
    SyncStatusRows(queue, paused, onRetry = viewModel::retryFailed)
    ui.consent?.let { sender ->
        Button(
            onClick = { consentLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.settings_sync_allow_files)) }
    }
    if (ui.consentDenied) Text(stringResource(R.string.settings_sync_allow_denied), Modifier.padding(top = 8.dp))
    OutlinedButton(
        onClick = { viewModel.signOut(context) },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(R.string.settings_sync_sign_out)) }
}

/** One line that says where sync stands, so nothing happens silently. */
@Composable
private fun SyncStatusRows(queue: SyncQueueCounts, paused: Boolean, onRetry: () -> Unit) {
    val text = when {
        paused -> stringResource(R.string.settings_sync_status_paused)
        queue.conflicts > 0 -> stringResource(R.string.settings_sync_status_conflicts, queue.conflicts)
        queue.failed > 0 -> stringResource(R.string.settings_sync_status_failed, queue.failed)
        queue.pending > 0 -> stringResource(R.string.settings_sync_status_pending, queue.pending)
        else -> stringResource(R.string.settings_sync_status_done)
    }
    Text(text, Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
    if (queue.failed > 0) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_sync_retry))
        }
    }
}

@Composable
private fun SyncSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).toggleable(checked, role = Role.Switch, onValueChange = onChecked),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun signInMessage(result: SignInResult?): String? = when (result) {
    SignInResult.NO_ACCOUNT -> stringResource(R.string.settings_sync_no_account)
    SignInResult.FAILED -> stringResource(R.string.settings_sync_failed)
    SignInResult.SIGNED_IN, SignInResult.CANCELLED, null -> null
}
