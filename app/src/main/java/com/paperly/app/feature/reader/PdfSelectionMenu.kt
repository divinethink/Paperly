package com.paperly.app.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * Both floating menus of the page area, placed next to what they act on with the same rule ([popupOrigin]):
 * the text-selection menu ([PdfSelectionPopup]) and the quick-box menu for scan pages ([PdfQuickMenu]).
 */
@Composable
internal fun PdfSelectionMenu(
    viewModel: ReaderViewModel,
    port: SelectionViewport,
    onEditor: (AnnotationEditor) -> Unit,
) {
    PdfSelectionPopup(viewModel, port, onEditor)
    PdfQuickMenu(viewModel, port, onEditor)
}

/** The area the menus may use: below the top bar and above the navigation bar. */
@Composable
internal fun selectionPort(
    listState: LazyListState,
    zoom: PageZoomState,
    basePx: Float,
    viewportPx: Float,
    heightPx: Float,
): SelectionViewport {
    val density = LocalDensity.current
    val edge = readerEdgePadding()
    return with(density) {
        SelectionViewport(
            listState,
            zoom,
            basePx,
            viewportPx,
            heightPx - edge.calculateBottomPadding().toPx(),
            topPx = edge.calculateTopPadding().toPx(),
        )
    }
}
