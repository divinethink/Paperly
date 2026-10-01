package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.EpubTypography
import com.paperly.app.domain.reader.ReaderTheme
import com.paperly.app.domain.reader.TextReadability

private const val BASE_TEXT_SP = 16f
private const val BASE_MARGIN_DP = 16f
private const val VERTICAL_PADDING_DP = 48
private val SINGLE_NEWLINE = Regex("(?<!\n)\n(?!\n)")

private data class ReflowPalette(val background: Color, val text: Color)

private sealed interface ReflowPageState {
    data object Loading : ReflowPageState
    data object Unavailable : ReflowPageState
    data class Ready(val text: String) : ReflowPageState
}

/**
 * Reflow (text) view of a text-layer PDF (P3-G): page text from the engine, shown with the EPUB typography
 * settings and reader theme. Own code path: it never reads or writes the PDF's saved page/progress.
 */
@Composable
internal fun ReflowPages(
    search: ReaderSearchController,
    pageCount: Int,
    settings: EpubSettingsViewModel = hiltViewModel(),
) {
    val typography by settings.typography.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val palette = paletteFor(theme, isSystemInDarkTheme())
    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = (BASE_MARGIN_DP * typography.margin).dp,
                vertical = VERTICAL_PADDING_DP.dp,
            ),
        ) {
            items(pageCount) { index -> ReflowPage(search, index, typography, palette.text) }
        }
        IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.epub_settings),
                tint = palette.text,
            )
        }
    }
    if (showSettings) {
        EpubSettingsSheet(
            typography = typography,
            theme = theme,
            onTypography = settings::updateTypography,
            onTheme = settings::setTheme,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ReflowPage(search: ReaderSearchController, index: Int, typography: EpubTypography, color: Color) {
    val state by produceState<ReflowPageState>(ReflowPageState.Loading, index) {
        value = reflowState(search.pageText(index))
    }
    val size = BASE_TEXT_SP * typography.fontScale
    val family = typography.font.composeFamily()
    when (val s = state) {
        ReflowPageState.Loading -> Unit
        ReflowPageState.Unavailable -> Text(
            stringResource(R.string.reader_reflow_unavailable),
            color = color,
            modifier = Modifier.padding(bottom = BASE_MARGIN_DP.dp),
        )
        is ReflowPageState.Ready -> Text(
            s.text,
            color = color,
            fontFamily = family,
            fontSize = size.sp,
            lineHeight = (size * typography.lineSpacing).sp,
            modifier = Modifier.padding(bottom = BASE_MARGIN_DP.dp),
        )
    }
}

/** null/failed or Bengali page -> Unavailable (never show broken text, Architecture 11.3); blank page -> empty. */
private fun reflowState(raw: String?): ReflowPageState = when {
    raw == null || TextReadability.hasBengali(raw) -> ReflowPageState.Unavailable
    else -> ReflowPageState.Ready(raw.replace(SINGLE_NEWLINE, " ").trim())
}

@Suppress("MagicNumber") // colour literals; local palette (debt: move to central tokens)
private fun paletteFor(theme: ReaderTheme, systemDark: Boolean): ReflowPalette = when (theme) {
    ReaderTheme.LIGHT -> ReflowPalette(Color(0xFFFFFFFF), Color(0xFF1A1A1A))
    ReaderTheme.SEPIA -> ReflowPalette(Color(0xFFF4ECD8), Color(0xFF5B4636))
    ReaderTheme.WARM -> ReflowPalette(Color(0xFFFFE9C7), Color(0xFF4A3B2A))
    ReaderTheme.DARK -> ReflowPalette(Color(0xFF121212), Color(0xFFDDDDDD))
    ReaderTheme.AMOLED -> ReflowPalette(Color(0xFF000000), Color(0xFFBBBBBB))
    ReaderTheme.AUTO -> paletteFor(if (systemDark) ReaderTheme.DARK else ReaderTheme.LIGHT, systemDark)
}
