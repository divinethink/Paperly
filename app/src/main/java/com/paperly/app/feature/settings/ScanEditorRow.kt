package com.paperly.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** S2: feature flag `scan_editor_enabled`. OFF = the plain scanner of before (no page editor, no gallery import). */
@Composable
fun ScanEditorRow(viewModel: ScanEditorViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val text = SwitchText(
        stringResource(R.string.settings_scan_editor),
        stringResource(R.string.settings_scan_editor_summary),
        R.string.settings_scan_editor_hint,
    )
    SettingSwitchRow(text, enabled, viewModel::setEnabled)
}
