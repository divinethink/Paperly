package com.paperly.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = NeutralText,
    surface = Color.White,
    onSurface = NeutralText,
    surfaceVariant = NeutralSurface,
    onSurfaceVariant = NeutralMuted,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    outline = NeutralOutline,
    outlineVariant = NeutralDivider,
    error = Danger,
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = NeutralSurface,
    surfaceContainerHigh = NeutralSurface,
    surfaceContainerHighest = NeutralSurface,
)

private val DarkColors = darkColorScheme(
    primary = TealOnDark,
    onPrimary = Color.Black,
    secondary = TealOnDark,
    onSecondary = Color.Black,
    background = NightBackground,
    onBackground = SoftGreyText,
    surface = NightBackground,
    onSurface = SoftGreyText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = SoftGreyText,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainer,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealContainer,
    outline = OutlineDark,
    outlineVariant = DividerDark,
    error = DangerDark,
    surfaceBright = Color(0xFF3A3632),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF2C2C2C),
)

/**
 * Brand palette is the default. Dynamic Color (Material You) is optional and OFF by default
 * (toggle arrives in P7). minSdk 31 so no API-level check is needed.
 */
@Composable
fun PaperlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = PaperlyTypography, content = content)
}
