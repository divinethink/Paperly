package com.paperly.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult

/** Settings -> Sync. Sign-in is optional; nothing else in the app depends on it. */
@Composable
fun SyncSection(viewModel: SyncAccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.padding(top = 24.dp)) {
        Text(
            stringResource(R.string.settings_sync_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(stringResource(R.string.settings_sync_hint), style = MaterialTheme.typography.bodyMedium)
        when (val a = account) {
            null -> Unit
            AuthState.Unavailable ->
                Text(stringResource(R.string.settings_sync_unavailable), Modifier.padding(top = 8.dp))
            AuthState.SignedOut -> Button(
                onClick = { viewModel.signIn(context) },
                enabled = !ui.busy,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.settings_sync_sign_in)) }
            is AuthState.SignedIn -> SignedInRows(a, ui, viewModel)
        }
        signInMessage(ui.result)?.let { Text(it, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun SignedInRows(account: AuthState.SignedIn, ui: SyncAccountUiState, viewModel: SyncAccountViewModel) {
    val context = LocalContext.current
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val counts by viewModel.fileCounts.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(it.data)
    }
    LaunchedEffect(account.uid) { viewModel.refreshAccess() }
    val name = account.email ?: account.uid
    Text(stringResource(R.string.settings_sync_signed_in_as, name), Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.settings_sync_files_hint), Modifier.padding(top = 8.dp))
    Text(stringResource(R.string.settings_sync_files_count, counts.synced, counts.total), Modifier.padding(top = 8.dp))
    ui.consent?.let { sender ->
        Button(
            onClick = { consentLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.settings_sync_allow_files)) }
    }
    if (ui.consentDenied) Text(stringResource(R.string.settings_sync_allow_denied), Modifier.padding(top = 8.dp))
    SyncSwitchRow(stringResource(R.string.settings_sync_wifi_only), wifiOnly, viewModel::setWifiOnly)
    if (!wifiOnly) {
        Text(stringResource(R.string.settings_sync_wifi_only_off), style = MaterialTheme.typography.bodySmall)
    }
    SyncSwitchRow(stringResource(R.string.settings_sync_pause), paused, viewModel::setPaused)
    OutlinedButton(
        onClick = { viewModel.signOut(context) },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(R.string.settings_sync_sign_out)) }
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
