@file:OptIn(ExperimentalLayoutApi::class)

package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity

/** Fixed (visibility-independent) room so page 1 starts below the top bar and the last page clears the nav bar. */
@Composable
internal fun readerEdgePadding() = PaddingValues(
    top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT,
    bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding(),
)

/** PDF full-page mode: hides / shows the system bars with the reader bars; always restored on leaving. */
@Composable
internal fun ReaderSystemBars(hidden: Boolean) {
    val window = (LocalContext.current as FragmentActivity).window
    val view = LocalView.current
    DisposableEffect(window, view) {
        onDispose { WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars()) }
    }
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
