package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.EpubFont
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.ReaderTheme

private const val THEMES_PER_ROW = 3

/** EPUB reading settings in a bottom sheet (page stays visible behind it). Changes apply and persist immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubSettingsSheet(
    typography: EpubTypography,
    theme: ReaderTheme,
    onTypography: (EpubTypography) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EpubSettingsRows(typography, theme, onTypography, onTheme, showMode = false)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.epub_done))
            }
        }
    }
}

/** Shared settings rows. [showMode] = Paged/Scroll switch (EPUB only; PDF reflow is always a scrolling view). */
@Composable
internal fun ColumnScope.EpubSettingsRows(
    typography: EpubTypography,
    theme: ReaderTheme,
    onTypography: (EpubTypography) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    showMode: Boolean,
) {
    if (showMode) {
        Text(stringResource(R.string.epub_mode))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckChip(!typography.scroll, R.string.epub_mode_paged) { onTypography(typography.copy(scroll = false)) }
            CheckChip(typography.scroll, R.string.epub_mode_scroll) { onTypography(typography.copy(scroll = true)) }
        }
    }
    Text(stringResource(R.string.epub_font))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EpubFont.entries.forEach { font ->
            CheckChip(typography.font == font, fontName(font)) { onTypography(typography.copy(font = font)) }
        }
    }
    Stepper(R.string.epub_font_size, "%.1f×".format(typography.fontScale)) { onTypography(typography.stepScale(it)) }
    Stepper(R.string.epub_line_spacing, "%.1f".format(typography.lineSpacing)) { onTypography(typography.stepLine(it)) }
    Stepper(R.string.epub_margin, "%.2f×".format(typography.margin)) { onTypography(typography.stepMargin(it)) }
    Text(stringResource(R.string.reader_theme))
    ThemeChips(theme, onTheme)
}

/** Selected chip shows a check mark, so the choice is clear without relying on colour alone. */
@Composable
private fun CheckChip(selected: Boolean, label: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun Stepper(label: Int, value: String, onStep: (up: Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        TextButton(onClick = { onStep(false) }) { Text("−") }
        Text(value)
        TextButton(onClick = { onStep(true) }) { Text("+") }
    }
}

@Composable
private fun ThemeChips(theme: ReaderTheme, onTheme: (ReaderTheme) -> Unit) {
    // Two rows so six chips fit a phone width.
    ReaderTheme.entries.chunked(THEMES_PER_ROW).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach {
                CheckChip(theme == it, themeName(it)) { onTheme(it) }
            }
        }
    }
}

private fun fontName(font: EpubFont): Int = when (font) {
    EpubFont.SERIF -> R.string.epub_font_serif
    EpubFont.SANS -> R.string.epub_font_sans
}
