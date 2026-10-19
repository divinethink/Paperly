@file:OptIn(ExperimentalLayoutApi::class)

package com.paperly.app.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.ArticleGlyph
import com.paperly.app.core.ui.icons.ShareGlyph
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.library.DocumentExport
import com.paperly.app.feature.library.rememberDocumentExport
import com.paperly.app.feature.scanner.LocalOpenScanEditor

private const val BAR_ALPHA = 0.94f
private const val SELECTED_ALPHA = 0.25f

/**
 * Floating top bar of the Reader (with the search / annotate bars). Scanned documents get the Notebloc teal bar;
 * every other document keeps the translucent reading-theme bar.
 */
@Composable
internal fun BoxScope.ReaderTopSurface(
    viewModel: ReaderViewModel,
    listState: LazyListState,
    fit: FitToggle,
    onBack: () -> Unit,
    onEditor: (AnnotationEditor) -> Unit,
) {
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    val scanPages: ScanPageViewModel = hiltViewModel()
    val isScan by scanPages.isScan.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val barColor = if (isScan) colors.primary else colors.surface.copy(alpha = BAR_ALPHA)
    // On the teal bar the "selected" tints (bookmark star, annotate toggle) use the bar's own on-colour.
    val scheme = if (isScan) {
        colors.copy(
            primary = colors.onPrimary,
            primaryContainer = colors.onPrimary.copy(alpha = SELECTED_ALPHA),
            onPrimaryContainer = colors.onPrimary,
        )
    } else {
        colors
    }
    if (isScan) LightStatusBarIcons()
    MaterialTheme(colorScheme = scheme) {
        Surface(
            Modifier.align(Alignment.TopStart).fillMaxWidth(),
            color = barColor,
            contentColor = if (isScan) colors.onPrimary else contentColorFor(barColor),
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
                ReaderTopBar(viewModel, listState, fit, onBack)
                if (search.open) SearchBar(viewModel.search, search)
                PdfAnnotateBars(viewModel.annotations, annotate, onEditor)
            }
        }
    }
}

/** White system-bar icons while the teal bar is under the status bar; the previous setting comes back on leaving. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/** Bottom teal bar for scanned documents: Note · OCR (copy scanned text) · Share · More. No Markup button. */
@Composable
internal fun BoxScope.ScanReaderBottomBar(doc: Document?, page: Int) {
    val scanPages: ScanPageViewModel = hiltViewModel()
    val isScan by scanPages.isScan.collectAsStateWithLifecycle()
    if (!isScan || doc == null) return
    val export = rememberDocumentExport(doc)
    val copyText = rememberCopyScanText(scanPages)
    var noting by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    if (noting) ScanPageEditor(page, scanPages) { noting = false }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val item = Modifier.weight(1f)
            BarButton(Icons.Filled.Edit, R.string.scan_page_note, item) { noting = true }
            BarButton(ArticleGlyph, R.string.scan_bar_ocr, item, copyText)
            BarButton(ShareGlyph, R.string.doc_share, item, export.share)
            Box(item) {
                BarButton(Icons.Filled.MoreVert, R.string.doc_menu_more, Modifier.fillMaxWidth()) { more = true }
                ScanMoreMenu(more, doc.id, export) { more = false }
            }
        }
    }
}

@Composable
private fun ScanMoreMenu(
    expanded: Boolean,
    documentId: String,
    export: DocumentExport,
    onDismiss: () -> Unit,
) {
    val scanPages: ScanPageViewModel = hiltViewModel()
    val canEdit by scanPages.canEditPages.collectAsStateWithLifecycle()
    val openEditor = LocalOpenScanEditor.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOfNotNull(
            if (canEdit) R.string.scan_edit_pages to { openEditor(documentId) } else null,
            R.string.doc_save_copy to export.saveCopy,
            export.print?.let { R.string.doc_print to it },
            export.exportImages?.let { R.string.doc_export_images to it },
        ).forEach { (label, action) ->
            MenuRow(label, null) {
                onDismiss()
                action()
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
