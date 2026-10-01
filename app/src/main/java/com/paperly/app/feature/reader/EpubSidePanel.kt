package com.paperly.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** Full-page panel sliding in from the start (left, Contents) or the end (right, Settings); system Back closes it. */
@Composable
internal fun SidePanel(
    visible: Boolean,
    fromStart: Boolean,
    title: Int,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val sign = if (fromStart) -1 else 1
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally { sign * it },
        exit = slideOutHorizontally { sign * it },
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        val label = stringResource(R.string.epub_close)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = label)
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                }
                content()
            }
        }
    }
}

/** Reading settings as a full page from the right. Changes apply and persist immediately. */
@Composable
internal fun EpubSettingsPage(visible: Boolean, settings: EpubSettingsViewModel, onDismiss: () -> Unit) {
    val typography by settings.typography.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    SidePanel(visible, fromStart = false, title = R.string.epub_settings, onDismiss = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EpubSettingsRows(typography, theme, settings::updateTypography, settings::setTheme, showMode = true)
        }
    }
}
