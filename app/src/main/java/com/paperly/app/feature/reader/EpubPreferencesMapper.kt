package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import com.paperly.app.domain.reader.EpubFont
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.ReaderTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Maps our typography + reader theme to Readium preferences. publisherStyles=false so our values take effect. */
@OptIn(ExperimentalReadiumApi::class)
internal fun EpubTypography.toReadium(theme: ReaderTheme, systemDark: Boolean) = EpubPreferences(
    fontFamily = FontFamily(font.family),
    fontSize = fontScale.toDouble(),
    lineHeight = lineSpacing.toDouble(),
    pageMargins = margin.toDouble(),
    publisherStyles = false,
    scroll = scroll,
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.SEPIA, ReaderTheme.WARM -> Theme.SEPIA
        ReaderTheme.DARK, ReaderTheme.AMOLED -> Theme.DARK
        ReaderTheme.AUTO -> if (systemDark) Theme.DARK else Theme.LIGHT
    },
)

private const val FONT_DIR = "fonts"

/** Serves the bundled fonts to the EPUB web view and declares each family (name = [EpubFont.family]). */
@OptIn(ExperimentalReadiumApi::class)
internal fun EpubNavigatorFragment.Configuration.withBundledFonts(): EpubNavigatorFragment.Configuration = apply {
    servedAssets += "$FONT_DIR/.*"
    EpubFont.entries.forEach { font ->
        val file = font.assetFile ?: return@forEach
        addFontFamilyDeclaration(FontFamily(font.family)) {
            addFontFace { addSource("$FONT_DIR/$file") }
        }
    }
}

/** Compose family for the PDF Reflow view: the bundled TTF, or the system generic for Serif/Sans. */
@Composable
internal fun EpubFont.composeFamily(): androidx.compose.ui.text.font.FontFamily {
    val assets = LocalContext.current.assets
    return remember(this) {
        when {
            assetFile != null ->
                androidx.compose.ui.text.font.FontFamily(Font(assets, "$FONT_DIR/$assetFile"))
            this == EpubFont.SANS -> androidx.compose.ui.text.font.FontFamily.SansSerif
            else -> androidx.compose.ui.text.font.FontFamily.Serif
        }
    }
}
