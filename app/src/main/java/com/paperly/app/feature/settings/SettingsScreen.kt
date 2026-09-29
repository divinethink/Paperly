package com.paperly.app.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** Sections (Reading/Scanner/Sync/...) are added with their phases; Storage arrives in P1. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.settings_storage_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        usage?.let { u ->
            UsageRow(
                pluralStringResource(R.plurals.settings_storage_documents, u.activeCount, u.activeCount),
                Formatter.formatShortFileSize(context, u.activeBytes),
            )
            UsageRow(
                pluralStringResource(R.plurals.settings_storage_trash, u.trashCount, u.trashCount),
                Formatter.formatShortFileSize(context, u.trashBytes),
            )
            UsageRow(
                stringResource(R.string.settings_storage_total),
                Formatter.formatShortFileSize(context, u.totalBytes),
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
