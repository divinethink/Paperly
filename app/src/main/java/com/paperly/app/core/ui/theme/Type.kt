package com.paperly.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// UI chrome: system sans (Roboto). Reading content and headings (U3): serif.
// Literata/Noto Serif Bengali will be bundled in P3 (reader); Serif is the placeholder.
val ReadingFontFamily: FontFamily = FontFamily.Serif

private val Base = Typography()

private fun TextStyle.serif() = copy(fontFamily = ReadingFontFamily)

val PaperlyTypography = Typography(
    headlineLarge = Base.headlineLarge.serif(),
    headlineMedium = Base.headlineMedium.serif(),
    headlineSmall = Base.headlineSmall.serif(),
    titleLarge = Base.titleLarge.serif(),
)
