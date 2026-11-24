package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import com.paperly.app.domain.settings.AppThemeMode
import com.paperly.app.feature.reader.ThemeRow

/** Light, dark or follow the phone, for the app screens. */
@Composable
internal fun AppThemeRow(viewModel: AppearanceViewModel = hiltViewModel()) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        RowTitle(R.string.settings_app_theme, R.string.settings_app_theme_summary)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = option == mode,
                    onClick = { viewModel.setThemeMode(option) },
                    label = { Text(stringResource(modeLabel(option))) },
                )
            }
        }
    }
}

/** Page colours of the reader (Auto, Light, Sepia, Warm, Dark, AMOLED); same choice as in the Reader menu. */
@Composable
internal fun ReadingThemeRow(viewModel: ReadingThemeViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        RowTitle(R.string.settings_reading_theme, R.string.settings_reading_theme_summary)
        Column(Modifier.padding(top = 8.dp)) { ThemeRow(theme, viewModel::setTheme) }
    }
}

@Composable
private fun RowTitle(title: Int, summary: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
    Text(
        stringResource(summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun modeLabel(mode: AppThemeMode): Int = when (mode) {
    AppThemeMode.SYSTEM -> R.string.settings_theme_system
    AppThemeMode.LIGHT -> R.string.settings_theme_light
    AppThemeMode.DARK -> R.string.settings_theme_dark
}
