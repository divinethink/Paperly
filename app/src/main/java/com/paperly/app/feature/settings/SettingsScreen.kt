package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.paperly.app.R

/** Shell only. Sections (Reading/Scanner/Sync/Storage...) are added with their phases. */
@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.settings_placeholder))
    }
}
