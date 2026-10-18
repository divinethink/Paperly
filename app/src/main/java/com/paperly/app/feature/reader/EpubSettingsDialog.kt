package com.paperly.app.feature.reader

import android.content.res.AssetManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.EpubFont
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.ReaderTheme

private const val FONTS_PER_ROW = 3
private const val FONT_GAP = 8
private const val FONT_RADIUS = 16
private const val FONT_CARD_MIN_HEIGHT = 72

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
        BlockLabel(R.string.epub_mode)
        PillSegmented(listOf(R.string.epub_mode_paged, R.string.epub_mode_scroll), if (typography.scroll) 1 else 0) {
            onTypography(typography.copy(scroll = it == 1))
        }
    }
    BlockLabel(R.string.epub_font)
    FontGrid(typography.font) { onTypography(typography.copy(font = it)) }
    SettingsCard(Modifier.padding(top = 16.dp)) {
        StepperRow(R.string.epub_font_size, "%.1f×".format(typography.fontScale)) {
            onTypography(typography.stepScale(it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StepperRow(R.string.epub_line_spacing, "%.1f".format(typography.lineSpacing)) {
            onTypography(typography.stepLine(it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StepperRow(R.string.epub_margin, "%.2f×".format(typography.margin)) { onTypography(typography.stepMargin(it)) }
    }
    BlockLabel(R.string.reader_theme)
    ThemeRow(theme, onTheme)
}

/** Fonts as a 3-column grid of cards, each previewing "অ Aa" in its own font. */
@Composable
private fun FontGrid(selected: EpubFont, onFont: (EpubFont) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FONT_GAP.dp)) {
        EpubFont.entries.chunked(FONTS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FONT_GAP.dp)) {
                row.forEach { font ->
                    FontCard(font, font == selected, Modifier.weight(1f)) { onFont(font) }
                }
                repeat(FONTS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FontCard(font: EpubFont, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val assets = LocalContext.current.assets
    val family = remember(font) { previewFamily(font, assets) }
    Surface(
        modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(FONT_RADIUS.dp),
        color = if (selected) colors.primaryContainer else Color.Transparent,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline),
    ) {
        Column(
            Modifier.heightIn(min = FONT_CARD_MIN_HEIGHT.dp).padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("অ Aa", style = MaterialTheme.typography.titleLarge.copy(fontFamily = family))
            Text(
                stringResource(fontName(font)),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Preview family: bundled TTF when the font has one, else the system generic; any load problem -> default. */
private fun previewFamily(font: EpubFont, assets: AssetManager): FontFamily = when {
    font.assetFile != null -> runCatching { FontFamily(Font("fonts/${font.assetFile}", assets)) }
        .getOrDefault(FontFamily.Default)
    font == EpubFont.SANS -> FontFamily.SansSerif
    else -> FontFamily.Serif
}

private fun fontName(font: EpubFont): Int = when (font) {
    EpubFont.SERIF -> R.string.epub_font_serif
    EpubFont.SANS -> R.string.epub_font_sans
    EpubFont.HIND_SILIGURI -> R.string.epub_font_hind_siliguri
    EpubFont.NOTO_SERIF_BENGALI -> R.string.epub_font_noto_serif_bengali
    EpubFont.INTER -> R.string.epub_font_inter
}
