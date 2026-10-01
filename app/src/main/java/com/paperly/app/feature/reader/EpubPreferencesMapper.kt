package com.paperly.app.feature.reader

import com.paperly.app.domain.reader.EpubFont
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.ReaderTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Maps our typography + reader theme to Readium preferences. publisherStyles=false so our values take effect. */
@OptIn(ExperimentalReadiumApi::class)
internal fun EpubTypography.toReadium(theme: ReaderTheme, systemDark: Boolean) = EpubPreferences(
    fontFamily = FontFamily(if (font == EpubFont.SERIF) "serif" else "sans-serif"),
    fontSize = fontScale.toDouble(),
    lineHeight = lineSpacing.toDouble(),
    pageMargins = margin.toDouble(),
    publisherStyles = false,
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.SEPIA, ReaderTheme.WARM -> Theme.SEPIA
        ReaderTheme.DARK, ReaderTheme.AMOLED -> Theme.DARK
        ReaderTheme.AUTO -> if (systemDark) Theme.DARK else Theme.LIGHT
    },
)
