package com.paperly.app.feature.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.ReaderTheme
import com.paperly.app.feature.scanner.LocalOpenScanEditor

/** Reader top-bar actions: Bookmark button, one-tap Annotate toggle, ⋮ menu (Fit mode, Comfort, Theme). */
@Composable
fun ReaderActions(viewModel: ReaderViewModel, page: Int, fitHeight: Boolean, onToggleFit: () -> Unit) {
    BookmarkAndAnnotate(viewModel, page)
    var showComfort by remember { mutableStateOf(false) }
    if (showComfort) ComfortSheet { showComfort = false }
    OverflowMenu(viewModel, page, fitHeight, onToggleFit) { showComfort = true }
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
            tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.Gray,
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
    onComfort: () -> Unit,
) {
    val current by viewModel.theme.collectAsStateWithLifecycle()
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
    if (editingPage) ScanPageEditor(page, scanPages) { editingPage = false }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        // Readable text layers use the text; scans/Bengali/broken text layers are read by OCR (P10). Once sampled.
        if (search.probed) {
            DropdownMenuItem(
                text = {
                    val label = if (reflow) R.string.reader_view_original else R.string.reader_view_reflow
                    Text(stringResource(label))
                },
                onClick = {
                    viewModel.toggleReflow()
                    open = false
                },
            )
        }
        SharePdfAnnotationsItem(viewModel) { open = false }
        files?.let { FileMenuItems(it) { open = false } }
        if (isScan) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_page_edit)) },
                onClick = {
                    editingPage = true
                    open = false
                },
            )
        }
        if (canEditPages) EditPagesMenuItem(scanPages.documentId) { open = false }
        DropdownMenuItem(
            text = { Text(stringResource(if (fitHeight) R.string.reader_fit_width else R.string.reader_fit_height)) },
            onClick = {
                onToggleFit()
                open = false
            },
        )
        ComfortMenuItem {
            onComfort()
            open = false
        }
        ThemeMenuItems(current, viewModel::setTheme) { open = false }
    }
}

/** S2: opens the scan page editor (navigation pops the Reader first: the editor replaces the file). */
@Composable
private fun EditPagesMenuItem(documentId: String, onDone: () -> Unit) {
    val openEditor = LocalOpenScanEditor.current
    DropdownMenuItem(
        text = { Text(stringResource(R.string.scan_edit_pages)) },
        onClick = {
            onDone()
            openEditor(documentId)
        },
    )
}

@Composable
private fun ThemeMenuItems(current: ReaderTheme, onPick: (ReaderTheme) -> Unit, onDone: () -> Unit) {
    ReaderTheme.entries.forEach { option ->
        val name = stringResource(R.string.reader_theme_item, stringResource(themeName(option)))
        DropdownMenuItem(
            text = { Text(if (option == current) "✓ $name" else name) },
            onClick = {
                onPick(option)
                onDone()
            },
        )
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
