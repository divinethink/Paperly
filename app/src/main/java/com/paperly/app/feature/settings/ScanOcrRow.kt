package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** S3: optional background text recognition of scans (`scan_ocr_enabled`, OFF by default). */
@Composable
fun ScanOcrRow(viewModel: ScanOcrViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val text = SwitchText(
        stringResource(R.string.settings_scan_ocr),
        stringResource(R.string.settings_scan_ocr_summary),
        R.string.settings_scan_ocr_hint,
    )
    Column {
        SettingSwitchRow(text, ui.enabled, viewModel::setEnabled, enabled = !ui.downloading)
        if (ui.downloading) {
            Text(stringResource(R.string.settings_scan_ocr_downloading), Modifier.padding(bottom = 8.dp))
        }
        if (ui.failed) {
            Text(
                stringResource(R.string.settings_scan_ocr_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
