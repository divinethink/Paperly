@file:OptIn(ExperimentalLayoutApi::class)

package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.ReaderTheme

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

/**
 * EPUB frame: page-coloured, with fixed (visibility-independent) system-bar room, so hiding / showing the
 * bars never re-flows the book. The bars float over this room (like the PDF reader).
 */
@Composable
internal fun EpubPageFrame(theme: ReaderTheme, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color(epubPageColor(theme, isSystemInDarkTheme())))
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility),
    ) { content() }
}

/** EPUB full page: system bars follow the reader bars (hidden by default); "Hide bars" keeps them hidden. */
@Composable
internal fun EpubSystemBars(barsVisible: Boolean, comfortViewModel: ReaderComfortViewModel = hiltViewModel()) {
    val comfort by comfortViewModel.comfort.collectAsStateWithLifecycle()
    ReaderSystemBars(hidden = !barsVisible || comfort.hideBars)
}
