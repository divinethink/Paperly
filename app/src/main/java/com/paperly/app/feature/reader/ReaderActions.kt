package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.FitHeightGlyph
import com.paperly.app.core.ui.icons.FitWidthGlyph
import com.paperly.app.core.ui.icons.ReflowGlyph
import com.paperly.app.domain.reader.ReaderTheme
import com.paperly.app.feature.scanner.LocalOpenScanEditor

private const val MENU_RADIUS = 24
private const val SEARCH_SLOT_DP = 48
private const val UNMARKED_ALPHA = 0.6f

/** What the top bar needs to flip Fit-width / Fit-height. */
internal class FitToggle(val fitHeight: Boolean, val onToggle: () -> Unit)

/** PDF Reader top bar: Back, search (when the text layer is readable) and, once pages exist, the actions. */
@Composable
internal fun ReaderTopBar(viewModel: ReaderViewModel, listState: LazyListState, fit: FitToggle, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val scanPages: ScanPageViewModel = hiltViewModel()
    val isScan by scanPages.isScan.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
        }
        if (isScan && state.pageCount > 0) {
            ScanTitleAndTools(scanPages, listState.firstVisibleItemIndex)
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (search.available) {
            IconButton(onClick = viewModel.search::toggle) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
            }
        } else if (!search.probed) {
            // Keeps the slot until the pages are sampled, so the icons on the right do not jump when Search appears.
            Spacer(Modifier.size(SEARCH_SLOT_DP.dp))
        }
        if (state.pageCount > 0) {
            ReaderActions(viewModel, listState.firstVisibleItemIndex, fit.fitHeight, fit.onToggle)
        }
    }
}

/** Reader top-bar actions: Bookmark, Annotate toggle, ⚙ (Theme + Comfort), ⋮ menu (Fit, Reflow, file actions). */
@Composable
fun ReaderActions(viewModel: ReaderViewModel, page: Int, fitHeight: Boolean, onToggleFit: () -> Unit) {
    BookmarkAndAnnotate(viewModel, page)
    var showComfort by remember { mutableStateOf(false) }
    if (showComfort) {
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        ComfortSheet(theme, viewModel::setTheme) { showComfort = false }
    }
    IconButton(onClick = { showComfort = true }) {
        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_display_settings))
    }
    OverflowMenu(viewModel, page, fitHeight, onToggleFit)
}

@Composable
private fun BookmarkAndAnnotate(viewModel: ReaderViewModel, page: Int) {
    val bookmarks by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
    val bookmarked = page in bookmarks
    IconButton(onClick = { viewModel.toggleBookmark(page) }) {
        Icon(
            Icons.Filled.Star,
            contentDescription = stringResource(
                if (bookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
            ),
            tint = if (bookmarked) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current.copy(alpha = UNMARKED_ALPHA)
            },
        )
    }
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    IconToggleButton(
        checked = annotate,
        onCheckedChange = { viewModel.annotations.toggleMode() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_annotate_mode))
    }
}

@Composable
private fun OverflowMenu(
    viewModel: ReaderViewModel,
    page: Int,
    fitHeight: Boolean,
    onToggleFit: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_more))
    }
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val scanPages: ScanPageViewModel = hiltViewModel()
    val isScan by scanPages.isScan.collectAsStateWithLifecycle()
    val canEditPages by scanPages.canEditPages.collectAsStateWithLifecycle()
    var editingPage by remember { mutableStateOf(false) }
    val files = rememberReaderFileActions(viewModel) // outside the dropdown: its launcher must outlive the menu
    val copyScanText = rememberCopyScanText(scanPages)
    if (editingPage) ScanPageEditor(page, scanPages) { editingPage = false }
    DropdownMenu(
        expanded = open,
        onDismissRequest = { open = false },
        shape = RoundedCornerShape(MENU_RADIUS.dp),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        // Readable text layers use the text; scans/Bengali/broken text layers are read by OCR (P10). Once sampled.
        if (search.probed) {
            MenuRow(if (reflow) R.string.reader_view_original else R.string.reader_view_reflow, ReflowGlyph) {
                viewModel.toggleReflow()
                open = false
            }
        }
        MenuRow(
            if (fitHeight) R.string.reader_fit_width else R.string.reader_fit_height,
            if (fitHeight) FitWidthGlyph else FitHeightGlyph,
        ) {
            onToggleFit()
            open = false
        }
        if (isScan) {
            ScanMenuItems(onPageNote = { editingPage = true }, onCopyText = copyScanText) { open = false }
        }
        if (canEditPages) EditPagesMenuItem(scanPages.documentId) { open = false }
        MenuDivider()
        files?.let { FileMenuItems(it) { open = false } }
        SharePdfAnnotationsItem(viewModel) { open = false }
        SelectLogMenuItem { open = false }
    }
}

/** S2: opens the scan page editor (navigation pops the Reader first: the editor replaces the file). */
@Composable
private fun EditPagesMenuItem(documentId: String, onDone: () -> Unit) {
    val openEditor = LocalOpenScanEditor.current
    MenuRow(R.string.scan_edit_pages, null) {
        onDone()
        openEditor(documentId)
    }
}

internal fun themeName(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.AUTO -> R.string.reader_theme_auto
    ReaderTheme.LIGHT -> R.string.reader_theme_light
    ReaderTheme.SEPIA -> R.string.reader_theme_sepia
    ReaderTheme.WARM -> R.string.reader_theme_warm
    ReaderTheme.DARK -> R.string.reader_theme_dark
    ReaderTheme.AMOLED -> R.string.reader_theme_amoled
}
