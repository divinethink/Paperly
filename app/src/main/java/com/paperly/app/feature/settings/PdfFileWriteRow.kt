package com.paperly.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** L3: Settings -> Reading. Highlights always stay in the app; this only mirrors them into the file. */
@Composable
fun PdfFileWriteRow(viewModel: PdfFileWriteViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val text = SwitchText(
        stringResource(R.string.settings_pdf_write),
        stringResource(R.string.settings_pdf_write_summary),
        R.string.settings_pdf_write_hint,
    )
    SettingSwitchRow(text, enabled, viewModel::setEnabled)
}
