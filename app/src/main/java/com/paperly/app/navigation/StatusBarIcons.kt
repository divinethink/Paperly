package com.paperly.app.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private const val LIGHT_BAR_LUMINANCE = 0.5f
private val TealBarRoutes = setOf(Routes.SETTINGS, Routes.TRASH, Routes.STORAGE, Routes.SCAN_PAGES, Routes.SCAN_EDIT)

private fun usesTealBar(route: String?) = Routes.isLibrary(route) || route in TealBarRoutes

private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/**
 * Screens with a teal title bar paint the status bar: the system icons must contrast with the bar colour
 * (white on the light-theme teal). The previous setting is restored when the route is left (Reader keeps its own).
 */
@Composable
internal fun TealStatusBarEffect(route: String?) {
    val view = LocalView.current
    val teal = usesTealBar(route)
    val barIsLight = MaterialTheme.colorScheme.primary.luminance() > LIGHT_BAR_LUMINANCE
    DisposableEffect(teal, barIsLight) {
        val window = if (teal) view.context.findWindow() else null
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = barIsLight
        onDispose { controller?.isAppearanceLightStatusBars = previous ?: barIsLight }
    }
}
