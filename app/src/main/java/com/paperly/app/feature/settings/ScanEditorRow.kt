package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** S2: feature flag `scan_editor_enabled`. OFF = the plain scanner of before (no page editor, no gallery import). */
@Composable
fun ScanEditorRow(viewModel: ScanEditorViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(enabled, role = Role.Switch, onValueChange = viewModel::setEnabled),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_scan_editor), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(stringResource(R.string.settings_scan_editor_hint), style = MaterialTheme.typography.bodySmall)
    }
}
