package com.paperly.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.feature.common.ErrorRetry
import com.paperly.app.feature.common.LoadingPlaceholder
import com.paperly.app.feature.library.rememberDocumentExport

/** Navigation callbacks of the page grid, grouped so the composables stay under the parameter-count limit. */
class ScanPagesActions(
    val onOpenReader: (documentId: String) -> Unit,
    val onAddScan: (documentId: String) -> Unit,
    val onAddPictures: (documentId: String) -> Unit,
)

/** Scanned document -> its pages (tap a page = open the Reader there). Back = Library. */
@Composable
fun ScanPagesScreen(
    onBack: () -> Unit,
    actions: ScanPagesActions,
    viewModel: ScanPagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val grid by viewModel.gridView.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.release() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    when (val ui = state) {
        ScanPagesUi.Loading -> PagesStatus(onBack) { LoadingPlaceholder() }
        ScanPagesUi.Failed ->
            PagesStatus(onBack) { ErrorRetry(stringResource(R.string.scan_pages_error), viewModel::reload) }
        is ScanPagesUi.Ready -> ScanPagesReady(ui, grid, viewModel, onBack, actions)
    }
}

@Composable
private fun PagesStatus(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PaperlyTopBar("", onBack = onBack)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ScanPagesReady(
    ready: ScanPagesUi.Ready,
    grid: Boolean,
    viewModel: ScanPagesViewModel,
    onBack: () -> Unit,
    actions: ScanPagesActions,
) {
    val doc = ready.document
    val export = rememberDocumentExport(doc)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PaperlyTopBar(doc.title, onBack = onBack) {
            ViewToggleButton(grid, viewModel::setGridView)
            PagesMenuButton(export)
        }
        val onPage = { index: Int -> viewModel.openPage(index) { actions.onOpenReader(doc.id) } }
        if (grid) {
            ScanPageGrid(ready, viewModel, onPage, Modifier.weight(1f))
        } else {
            ScanPageList(ready, viewModel, onPage, Modifier.weight(1f))
        }
        PagesBottomBar(
            export,
            onPictures = { actions.onAddPictures(doc.id) },
            onScan = { actions.onAddScan(doc.id) },
        )
    }
}
