package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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

/** EPUB reading settings: font, size, line spacing, margin, theme (P3-C). Changes apply and persist immediately. */
@Composable
fun EpubSettingsDialog(
    typography: EpubTypography,
    theme: ReaderTheme,
    onTypography: (EpubTypography) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.epub_settings)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.epub_done)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.epub_font))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EpubFont.entries.forEach { font ->
                        FilterChip(
                            selected = typography.font == font,
                            onClick = { onTypography(typography.copy(font = font)) },
                            label = { Text(stringResource(fontName(font))) },
                        )
                    }
                }
                Stepper(R.string.epub_font_size, "%.1f×".format(typography.fontScale)) {
                    onTypography(typography.stepScale(it))
                }
                Stepper(R.string.epub_line_spacing, "%.1f".format(typography.lineSpacing)) {
                    onTypography(typography.stepLine(it))
                }
                Stepper(R.string.epub_margin, "%.2f×".format(typography.margin)) {
                    onTypography(typography.stepMargin(it))
                }
                Text(stringResource(R.string.reader_theme))
                ThemeChips(theme, onTheme)
            }
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
                FilterChip(
                    selected = theme == it,
                    onClick = { onTheme(it) },
                    label = { Text(stringResource(themeName(it))) },
                )
            }
        }
    }
}

private fun fontName(font: EpubFont): Int = when (font) {
    EpubFont.SERIF -> R.string.epub_font_serif
    EpubFont.SANS -> R.string.epub_font_sans
}
