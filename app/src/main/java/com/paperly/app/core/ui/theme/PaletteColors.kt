package com.paperly.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.paperly.app.domain.settings.AppPalette

// Extra app palettes. Base colours come from the Hisheb app's Treasury / Nord Frost / Bull Market themes; the
// container, outline and divider tones are tints derived from them. Roles are the same ones Theme.kt fills.

/** Meadow: green, cream and gold-leaf paper (light). */
private val MeadowColors = lightColorScheme(
    primary = Color(0xFF14532D),
    onPrimary = Color.White,
    secondary = Color(0xFF14532D),
    onSecondary = Color.White,
    tertiary = Color(0xFF14532D),
    onTertiary = Color.White,
    background = Color(0xFFFBF8EA),
    onBackground = Color(0xFF163422),
    surface = Color(0xFFFBF8EA),
    onSurface = Color(0xFF163422),
    surfaceVariant = Color(0xFFEEF0DE),
    onSurfaceVariant = Color(0xFF546856),
    primaryContainer = Color(0xFFD5E6CF),
    onPrimaryContainer = Color(0xFF0F3A20),
    secondaryContainer = Color(0xFFD5E6CF),
    onSecondaryContainer = Color(0xFF0F3A20),
    outline = Color(0xFF8A9A88),
    outlineVariant = Color(0xFFD3DCC6),
    error = Color(0xFFB91C1C),
    surfaceBright = Color(0xFFFFFEF6),
    surfaceContainerLowest = Color(0xFFFBF8EA),
    surfaceContainerLow = Color(0xFFFBF8EA),
    surfaceContainer = Color(0xFFEEF0DE),
    surfaceContainerHigh = Color(0xFFE6EEDE),
    surfaceContainerHighest = Color(0xFFE6EEDE),
)

/** Nord Frost (key "glacier"): calm blue-grey (light). */
private val GlacierColors = lightColorScheme(
    primary = Color(0xFF3E6092),
    onPrimary = Color.White,
    secondary = Color(0xFF3E6092),
    onSecondary = Color.White,
    tertiary = Color(0xFF3E6092),
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF222A3C),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF222A3C),
    surfaceVariant = Color(0xFFE8EDF6),
    onSurfaceVariant = Color(0xFF546078),
    primaryContainer = Color(0xFFD6E2F5),
    onPrimaryContainer = Color(0xFF17284A),
    secondaryContainer = Color(0xFFD6E2F5),
    onSecondaryContainer = Color(0xFF17284A),
    outline = Color(0xFF8A96AD),
    outlineVariant = Color(0xFFD0D8E6),
    error = Color(0xFFB83848),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color(0xFFF7F9FC),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFE8EDF6),
    surfaceContainerHigh = Color(0xFFE2E7F2),
    surfaceContainerHighest = Color(0xFFE2E7F2),
)

/** Neon Night: near-black with neon green (dark). */
private val NeonNightColors = darkColorScheme(
    primary = Color(0xFF1EE88C),
    onPrimary = Color.Black,
    secondary = Color(0xFF1EE88C),
    onSecondary = Color.Black,
    tertiary = Color(0xFF1EE88C),
    onTertiary = Color.Black,
    background = Color(0xFF11181A),
    onBackground = Color(0xFFECF4F0),
    surface = Color(0xFF11181A),
    onSurface = Color(0xFFECF4F0),
    surfaceVariant = Color(0xFF1C2628),
    onSurfaceVariant = Color(0xFF8FA39B),
    primaryContainer = Color(0xFF0F4D31),
    onPrimaryContainer = Color(0xFFBFF5DB),
    secondaryContainer = Color(0xFF0F4D31),
    onSecondaryContainer = Color(0xFFBFF5DB),
    outline = Color(0xFF44564F),
    outlineVariant = Color(0xFF2A3836),
    error = Color(0xFFFF5A5A),
    surfaceBright = Color(0xFF2C3A3C),
    surfaceContainerLowest = Color(0xFF05080A),
    surfaceContainerLow = Color(0xFF151E20),
    surfaceContainer = Color(0xFF1C2628),
    surfaceContainerHigh = Color(0xFF223032),
    surfaceContainerHighest = Color(0xFF283A3C),
)

/** The colour scheme of an extra palette; null for [AppPalette.PAPERLY] (the brand palette lives in Theme.kt). */
internal fun paletteColors(palette: AppPalette): ColorScheme? = when (palette) {
    AppPalette.PAPERLY -> null
    AppPalette.MEADOW -> MeadowColors
    AppPalette.GLACIER -> GlacierColors
    AppPalette.NEON_NIGHT -> NeonNightColors
}
