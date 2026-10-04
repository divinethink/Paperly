package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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

/** Settings > Storage: look for, then (on request) delete, document files that no document refers to. */
@Composable
fun OrphanFilesSection(viewModel: OrphanFilesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_orphans_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = viewModel::scan,
            enabled = state != OrphanUi.Busy,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.settings_orphans_check)) }
        when (val s = state) {
            OrphanUi.Idle, OrphanUi.Busy -> Unit
            is OrphanUi.Found -> if (s.count == 0) {
                Text(stringResource(R.string.settings_orphans_none), Modifier.padding(top = 8.dp))
            } else {
                Text(
                    stringResource(R.string.settings_orphans_found, s.count, Formatter.formatShortFileSize(context, s.bytes)),
                    Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = viewModel::clean, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_orphans_clean))
                }
            }
            is OrphanUi.Done -> Text(
                stringResource(R.string.settings_orphans_done, s.deleted, s.failed),
                Modifier.padding(top = 8.dp),
            )
            OrphanUi.Failed -> Text(stringResource(R.string.settings_orphans_failed), Modifier.padding(top = 8.dp))
        }
    }
}
