package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

/**
 * Floating menus that sit on top of the page area but outside the zoomed list. The text-selection menu
 * (Copy | Highlight | Underline | Note) lives in [PdfSelectionPopup], next to the selected text; here only the
 * quick-box menu remains.
 */
@Composable
internal fun BoxScope.PdfSelectionMenu(viewModel: ReaderViewModel, onEditor: (AnnotationEditor) -> Unit) {
    PdfQuickMenu(viewModel, onEditor)
}
