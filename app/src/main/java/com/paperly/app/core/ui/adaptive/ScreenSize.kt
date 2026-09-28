package com.paperly.app.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Same breakpoints as Material WindowSizeClass (600dp / 840dp); no extra dependency needed. */
enum class ScreenSize { Compact, Medium, Expanded }

@Composable
fun rememberScreenSize(): ScreenSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> ScreenSize.Compact
        widthDp < 840 -> ScreenSize.Medium
        else -> ScreenSize.Expanded
    }
}
