package com.paperly.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette (Architecture §৬.১). Exact hex fine-tunable later; only this file changes.

// Primary: deep ink/charcoal
val Ink = Color(0xFF23272E)

// Light background: off-white paper tone
val Paper = Color(0xFFFBF7F2)
val InkText = Color(0xFF1F1A17)

// Accent: warm sepia/amber
val Sepia = Color(0xFFB07A3A)
val PaperVariant = Color(0xFFEFE9DF)

// Dark theme
val InkOnDark = Color(0xFFDDD8CE)
val NightBackground = Color(0xFF121212)
val NightSurfaceVariant = Color(0xFF232323)
val SepiaOnDark = Color(0xFFD9A55B)
val SoftGreyText = Color(0xFFCFCBC3)

// U3: the single warm accent (primary + secondary). Light value darkened for >=4.5:1 on Paper.
val Accent = Color(0xFF8A5A25)

// U4: letter-cover backgrounds (warm, muted; white text >=4.5:1). Index picked by title hash.
val CoverPalette = listOf(
    Color(0xFF8A5A22),
    Color(0xFF7A4B3A),
    Color(0xFF5E6B3F),
    Color(0xFF4F6470),
    Color(0xFF6B4F70),
    Color(0xFF8A4A4A),
    Color(0xFF3F6B5E),
    Color(0xFF6E5A2E),
)

// UI refresh tokens (light / dark pairs). Roles are wired in Theme.kt so screens read MaterialTheme only.
val MutedText = Color(0xFF6B625B)
val Tonal = Color(0xFFF1E0CC)
val OnTonal = Color(0xFF5A3A12)
val SurfaceSoft = Color(0xFFF1EAE1)
val CardSoft = Color(0xFFF4ECE2)
val OutlineSoft = Color(0xFFD9CFC4)
val DividerSoft = Color(0xFFE6DBCD)
val Danger = Color(0xFFB3362A)
val TonalDark = Color(0xFF4A3620)
val OutlineDark = Color(0xFF4A4540)
val DividerDark = Color(0xFF3A3632)
val DangerDark = Color(0xFFF2B8B0)
val TrashGreen = Color(0xFF5E6B3F)

// Notebloc-style look (light): teal bars, white pages, neutral grey text/dividers.
// Teal 800 keeps white-on-bar text at >=4.5:1 (AA).
val Teal = Color(0xFF00838F)
val TealContainer = Color(0xFFCDEFF3)
val OnTealContainer = Color(0xFF00363D)
val TealOnDark = Color(0xFF4DD0E1)
val TealContainerDark = Color(0xFF004F58)
val NeutralText = Color(0xFF212121)
val NeutralMuted = Color(0xFF616161)
val NeutralSurface = Color(0xFFF3F5F6)
val NeutralOutline = Color(0xFFBDBDBD)
val NeutralDivider = Color(0xFFE0E0E0)
