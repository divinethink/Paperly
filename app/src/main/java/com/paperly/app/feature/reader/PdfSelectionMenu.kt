package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable

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
