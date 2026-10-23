package com.paperly.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.folder.MAX_FOLDER_DEPTH
import com.paperly.app.feature.common.EmptyState
import com.paperly.app.feature.common.ErrorRetry
import com.paperly.app.feature.common.LoadingPlaceholder

@StringRes
private fun ImportError.messageRes(): Int = when (this) {
    ImportError.Unsupported -> R.string.import_error_unsupported
    ImportError.Failed -> R.string.import_error_failed
}

@StringRes
private fun LibraryFilter.emptyRes(): Int = when (this) {
    LibraryFilter.All -> R.string.library_empty
    LibraryFilter.Favorites -> R.string.favorites_empty
    LibraryFilter.Recent -> R.string.recent_empty
    LibraryFilter.Finished -> R.string.finished_empty
}

/** Per-document callbacks, grouped so composables stay under the parameter-count limit. */
internal class DocumentActions(
    val onOpen: (Document) -> Unit,
    val onToggleFavorite: (Document) -> Unit,
    val onRename: (Document) -> Unit,
    val onMove: (Document) -> Unit,
    val onTags: (Document) -> Unit,
    val onTrash: (Document) -> Unit,
)

@Composable
fun LibraryScreen(
    navigation: LibraryNavigation,
    viewModel: LibraryViewModel = hiltViewModel(),
    organize: OrganizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val batch = rememberBatchState()
    val libraryTitle = stringResource(viewModel.type.nameRes())
    val isScanTab = viewModel.type == LibraryType.SCAN
    LibraryTabEffects(viewModel, navigation.onShowTab)
    val startImport = rememberImportStarter(viewModel)
    val actions = DocumentActions(
        onOpen = { navigation.onOpenReader(it.id) },
        onToggleFavorite = viewModel::toggleFavorite,
        onRename = { dialog = LibraryDialog.Rename(it) },
        onMove = { dialog = LibraryDialog.Move(it) },
        onTags = { dialog = LibraryDialog.Tags(it) },
        onTrash = viewModel::moveToTrash,
    )

    state.duplicate?.let { DuplicateHost(it, viewModel, navigation) }
    dialog?.let { LibraryDialogHost(it, state.folders, viewModel, organize) { dialog = null } }

    // Back goes up one folder level first (then the usual back behaviour)
    BackHandler(enabled = state.selectedFolderId != null) {
        viewModel.selectFolder(state.parentFolderId())
    }
    val folderUi = folderUi(state, viewModel, organize, navigation) { dialog = it }
    val listActions = ListActions(startImport, navigation.onScan, viewModel::retry, viewModel::selectFolder)

    Column(Modifier.fillMaxSize()) {
        if (batch.active) {
            Box(Modifier.statusBarsPadding()) { BatchBar(batch, state.documents, state.folders) }
        } else {
            val header = HeaderInfo(libraryTitle, state.totalCount, state.isImporting, viewModel.type)
            val canAddFolder = state.folderPath.size < MAX_FOLDER_DEPTH
            val newFolder = { dialog = LibraryDialog.NewFolder(state.selectedFolderId) }
            LibraryHeader(header, navigation, canAddFolder, newFolder, startImport)
        }
        SearchRow(state.query, viewModel::setQuery, state.view)
        FilterRow(state.filter, viewModel::setFilter)
        FolderSection(isScanTab, state, folderUi)
        // "Continue reading" is a reading shortcut: not shown on the Scanned tab
        if (viewModel.type != LibraryType.SCAN) {
            ContinueSection(viewModel.continueReading, state.query, navigation.onOpenReader)
        }
        ImageExportStatus()
        DocxConversionStatus(navigation.onOpenReader)
        LibraryList(state, viewModel.type, actions, batch, listActions)
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    type: LibraryType,
    actions: DocumentActions,
    batch: BatchState,
    listActions: ListActions,
) {
    if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { err ->
        Text(
            stringResource(err.messageRes()),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    when {
        state.load == LibraryLoad.Loading -> LoadingPlaceholder()
        state.load == LibraryLoad.Failed ->
            ErrorRetry(stringResource(R.string.library_load_error), listActions.onRetry)
        state.documents.isEmpty() && state.scanFolders.isEmpty() -> LibraryEmpty(state, type, listActions)
        state.documents.all { it.type == DocumentType.SCANNED_PDF } ->
            ScanDocumentList(state, actions, batch, hiltViewModel(), listActions.onOpenFolder)
        else -> DocumentList(state, actions, batch, hiltViewModel(), listActions.onOpenFolder)
    }
}

@Composable
private fun LibraryEmpty(state: LibraryUiState, type: LibraryType, listActions: ListActions) {
    val searching = state.query.isNotBlank() || state.selectedFolderId != null
    val emptyRes = when {
        state.totalCount == 0 -> type.emptyRes()
        searching -> R.string.search_empty
        else -> state.filter.emptyRes()
    }
    if (state.totalCount == 0) {
        EmptyWithActions(stringResource(emptyRes), type, listActions.onImport, listActions.onScan)
    } else {
        EmptyState(stringResource(emptyRes))
    }
}

/** What the header shows, grouped so [LibraryHeader] stays under the parameter-count limit. */
private class HeaderInfo(val title: String, val count: Int, val isImporting: Boolean, val type: LibraryType)

@Composable
private fun LibraryHeader(
    info: HeaderInfo,
    navigation: LibraryNavigation,
    canAddFolder: Boolean,
    onNewFolder: () -> Unit,
    onImport: (ImportKind) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Notebloc-style teal title bar; the background also paints the status-bar area.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                info.title + " (" + info.count + ")",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ImportAction(info.type, enabled = !info.isImporting, onImport = onImport)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canAddFolder) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.folder_new)) },
                            onClick = {
                                menuOpen = false
                                onNewFolder()
                            },
                        )
                    }
                    LibraryMenuItems(info.type == LibraryType.SCAN, navigation) { menuOpen = false }
                }
            }
        }
    }
}
