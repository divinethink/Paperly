package com.paperly.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Reading content uses serif (Reader/cover letters). UI chrome and headings: system sans (Notebloc-style).
val ReadingFontFamily: FontFamily = FontFamily.Serif

private val base = Typography()

// Headings in serif; body, labels and small titles stay system sans.
val PaperlyTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontFamily = ReadingFontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = ReadingFontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = ReadingFontFamily),
)
