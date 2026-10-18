package com.paperly.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.paperly.app.R
import com.paperly.app.core.ui.icons.ChevronGlyph

private const val DANGER_BORDER_ALPHA = 0.5f
private const val CHEVRON_DOWN = 90f
private const val LabelTracking = 0.06f
private val RowMinHeight = 56.dp

/** Small caps label above a group of cards (Reading / Data / Privacy / Danger zone). */
@Composable
internal fun GroupLabel(@StringRes label: Int, danger: Boolean = false) {
    Text(
        stringResource(label).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = LabelTracking.em),
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** One settings group: a rounded card (outlined in red for the danger zone). */
@Composable
internal fun SettingsCard(danger: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = if (danger) {
        CardDefaults.cardColors(containerColor = Color.Transparent)
    } else {
        CardDefaults.cardColors()
    }
    val dangerLine = MaterialTheme.colorScheme.error.copy(alpha = DANGER_BORDER_ALPHA)
    val border = if (danger) BorderStroke(1.dp, dangerLine) else null
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = colors,
        border = border,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

@Composable
internal fun SettingsDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Summary(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun CardHeader(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

/** Title + one-line summary on the left, value on the right (read-only). */
@Composable
internal fun ValueRow(title: String, summary: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Summary(summary)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Texts of a switch row: title, one-line summary and (optional) the long explanation shown behind the info button. */
internal class SwitchText(val title: String, val summary: String, @StringRes val info: Int? = null)

/**
 * Switch row: title + short summary. A long explanation goes behind an info button (dialog), so no text is lost.
 * The text column carries the switch semantics; the visible Switch only draws the state.
 */
@Composable
internal fun SettingSwitchRow(
    text: SwitchText,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            ).padding(vertical = 8.dp),
        ) {
            Text(text.title, style = MaterialTheme.typography.bodyLarge)
            Summary(text.summary)
        }
        if (text.info != null) {
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.settings_info))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
    if (showInfo && text.info != null) {
        InfoDialog(text.title, stringResource(text.info)) { showInfo = false }
    }
}

@Composable
internal fun InfoDialog(title: String, text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(text, textAlign = TextAlign.Start) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.settings_info_close)) } },
    )
}

/** A tappable row with a chevron; [expanded] (when not null) turns the chevron down to show an opened section. */
@Composable
internal fun ArrowRow(title: String, onClick: () -> Unit, enabled: Boolean = true, expanded: Boolean? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Icon(
            ChevronGlyph,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(if (expanded == true) CHEVRON_DOWN else 0f),
        )
    }
}
