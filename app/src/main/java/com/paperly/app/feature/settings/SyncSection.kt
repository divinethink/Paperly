package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
            is AuthState.SignedIn -> {
                Text(
                    stringResource(R.string.settings_sync_signed_in_as, a.email ?: a.uid),
                    Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.signOut(context) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.settings_sync_sign_out)) }
            }
        }
        signInMessage(ui.result)?.let { Text(it, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun signInMessage(result: SignInResult?): String? = when (result) {
    SignInResult.NO_ACCOUNT -> stringResource(R.string.settings_sync_no_account)
    SignInResult.FAILED -> stringResource(R.string.settings_sync_failed)
    SignInResult.SIGNED_IN, SignInResult.CANCELLED, null -> null
}
