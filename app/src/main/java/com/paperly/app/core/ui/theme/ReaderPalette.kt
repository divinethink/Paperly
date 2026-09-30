package com.paperly.app.core.ui.theme

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.paperly.app.domain.reader.ReaderTheme

/** Reader-only colors. [pageFilter] recolors the (white) PDF page bitmap; null = draw as-is. */
data class ReaderPalette(val background: Color, val content: Color, val pageFilter: ColorFilter?)

private val SepiaBackground = Color(0xFFF4ECD8)
private val SepiaContent = Color(0xFF5B4636)
private val WarmBackground = Color(0xFFFFE2B8)
private val WarmContent = Color(0xFF4A3B2A)
private val DarkContent = Color(0xFFD2D2D2)

/** out = -scale * in + offset (per RGB channel, 0..255): white -> dark, black text -> soft grey. */
private fun invert(scale: Float, offset: Float) = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -scale, 0f, 0f, 0f, offset,
            0f, -scale, 0f, 0f, offset,
            0f, 0f, -scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private val LightPalette = ReaderPalette(Paper, InkText, null)
private val SepiaPalette =
    ReaderPalette(SepiaBackground, SepiaContent, ColorFilter.tint(SepiaBackground, BlendMode.Multiply))
private val WarmPalette =
    ReaderPalette(WarmBackground, WarmContent, ColorFilter.tint(WarmBackground, BlendMode.Multiply))
private val DarkPalette = ReaderPalette(Color(0xFF1C1C1C), DarkContent, invert(scale = 0.714f, offset = 210f))
private val AmoledPalette = ReaderPalette(Color.Black, DarkContent, invert(scale = 0.843f, offset = 215f))

/** AUTO follows the system dark setting. */
fun readerPalette(theme: ReaderTheme, systemDark: Boolean): ReaderPalette = when (theme) {
    ReaderTheme.AUTO -> if (systemDark) DarkPalette else LightPalette
    ReaderTheme.LIGHT -> LightPalette
    ReaderTheme.SEPIA -> SepiaPalette
    ReaderTheme.WARM -> WarmPalette
    ReaderTheme.DARK -> DarkPalette
    ReaderTheme.AMOLED -> AmoledPalette
}
