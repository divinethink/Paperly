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

/** S3: optional background text recognition of scans (`scan_ocr_enabled`, OFF by default). */
@Composable
fun ScanOcrRow(viewModel: ScanOcrViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = ui.enabled,
                enabled = !ui.downloading,
                role = Role.Switch,
                onValueChange = viewModel::setEnabled,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_scan_ocr), Modifier.weight(1f))
            Switch(checked = ui.enabled, onCheckedChange = null, enabled = !ui.downloading)
        }
        Text(stringResource(R.string.settings_scan_ocr_hint), style = MaterialTheme.typography.bodySmall)
        if (ui.downloading) Text(stringResource(R.string.settings_scan_ocr_downloading))
        if (ui.failed) {
            Text(stringResource(R.string.settings_scan_ocr_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
