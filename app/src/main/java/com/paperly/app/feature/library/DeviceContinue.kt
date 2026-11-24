package com.paperly.app.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.file.DeviceRecent
import kotlinx.coroutines.flow.StateFlow

/** "Continue from device": the last PDF/EPUB read straight from the phone; separate from the Library's card. */
@Composable
internal fun DeviceContinueSection(source: StateFlow<DeviceRecent?>, query: String, onOpen: () -> Unit) {
    val recent by source.collectAsStateWithLifecycle()
    val current = recent
    if (current != null && query.isBlank()) {
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.device_continue_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current.page?.let { page ->
                    Text(
                        stringResource(R.string.device_continue_page, page + 1),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
