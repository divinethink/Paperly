package com.paperly.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R

/**
 * Settings > Privacy: "Export my data" (the same verified backup ZIP, started through the same runner, so it also
 * survives leaving the screen) and the list of what leaves the phone.
 */
@Composable
fun PrivacySection(busy: Boolean, onExport: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(
            stringResource(R.string.settings_privacy_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(stringResource(R.string.settings_privacy_export_hint), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onExport, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.settings_privacy_export))
        }
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(if (expanded) R.string.settings_privacy_hide else R.string.settings_privacy_show))
        }
        if (expanded) {
            Text(stringResource(R.string.settings_privacy_intro), Modifier.padding(top = 8.dp))
            PrivacyDisclosure.items.forEach { DisclosureRow(it) }
            Text(
                stringResource(R.string.settings_privacy_signout_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun DisclosureRow(item: DisclosureItem) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(item.condition), style = MaterialTheme.typography.labelMedium)
        Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium)
    }
}
