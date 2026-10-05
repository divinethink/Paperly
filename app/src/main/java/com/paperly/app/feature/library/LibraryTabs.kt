package com.paperly.app.feature.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.paperly.app.R
import com.paperly.app.domain.document.LibraryType

/** Screen-level navigation callbacks, grouped so the composables stay under the parameter-count limit. */
class LibraryNavigation(
    val onOpenReader: (documentId: String) -> Unit,
    val onOpenTrash: () -> Unit,
    val onOpenStorage: () -> Unit,
    val onShowTab: (LibraryType) -> Unit,
)

@StringRes
internal fun LibraryType.titleRes(): Int = when (this) {
    LibraryType.SCAN -> R.string.library_title_scanned
    LibraryType.PDF -> R.string.library_title_pdf
    LibraryType.EPUB -> R.string.library_title_epub
}

/** Tab-level side effects: incoming Share/Open-With imports and "jump to the tab that holds the new document". */
@Composable
internal fun LibraryTabEffects(viewModel: LibraryViewModel, onShowTab: (LibraryType) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Handled only by the tab that is on screen, so with several live tabs one request is imported once.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.processIncomingImports() }
    }
    LaunchedEffect(viewModel) {
        viewModel.showTab.collect { if (it != viewModel.type) onShowTab(it) }
    }
}
