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
    primary = Ink,
    onPrimary = Paper,
    secondary = Sepia,
    onSecondary = Color.White,
    background = Paper,
    onBackground = InkText,
    surface = Paper,
    onSurface = InkText,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkText,
)

private val DarkColors = darkColorScheme(
    primary = InkOnDark,
    onPrimary = Ink,
    secondary = SepiaOnDark,
    onSecondary = Color.Black,
    background = NightBackground,
    onBackground = SoftGreyText,
    surface = NightBackground,
    onSurface = SoftGreyText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = SoftGreyText,
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
