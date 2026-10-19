package com.paperly.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.DownloadGlyph
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.common.EmptyState
import com.paperly.app.feature.common.ErrorRetry
import com.paperly.app.feature.common.LoadingPlaceholder
import com.paperly.app.feature.common.documentMeta

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
}

private val ImportMimeTypes = arrayOf("application/pdf", "application/epub+zip", DOCX_MIME)

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
    val context = LocalContext.current
    LibraryTabEffects(viewModel, navigation.onShowTab)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri == null -> Unit
            isDocx(context, uri) -> enqueueDocxConversion(context, uri) // Word file -> background EPUB conversion
            else -> viewModel.importDocument(uri.toString())
        }
    }
    val actions = DocumentActions(
        onOpen = { navigation.onOpenReader(it.id) },
        onToggleFavorite = viewModel::toggleFavorite,
        onRename = { dialog = LibraryDialog.Rename(it) },
        onMove = { dialog = LibraryDialog.Move(it) },
        onTags = { dialog = LibraryDialog.Tags(it) },
        onTrash = viewModel::moveToTrash,
    )

    state.duplicate?.let { dup ->
        DuplicateDialog(
            prompt = dup,
            onSkip = viewModel::dismissDuplicate,
            onKeepBoth = viewModel::keepBoth,
            onOpenExisting = {
                viewModel.dismissDuplicate()
                navigation.onOpenReader(dup.existingId)
            },
        )
    }
    dialog?.let { LibraryDialogHost(it, state.folders, viewModel, organize) { dialog = null } }

    Column(Modifier.fillMaxSize()) {
        if (batch.active) {
            BatchBar(batch, state.documents, state.folders)
        } else {
            val isScanTab = viewModel.type == LibraryType.SCAN
            val header = HeaderInfo(libraryTitle, state.totalCount, state.isImporting, isScanTab)
            LibraryHeader(header, navigation) { picker.launch(ImportMimeTypes) }
        }
        SearchRow(state.query, viewModel::setQuery, state.view)
        FilterRow(state.filter, viewModel::setFilter)
        FolderRow(
            folders = state.folders,
            selectedId = state.selectedFolderId,
            onSelect = viewModel::selectFolder,
            onNew = { dialog = LibraryDialog.NewFolder },
            onDelete = { deleteFolderAction(state, it, organize)?.let { d -> dialog = d } },
        )
        // "Continue reading" is a reading shortcut: not shown on the Scanned tab
        if (viewModel.type != LibraryType.SCAN) {
            ContinueSection(viewModel.continueReading, state.query, navigation.onOpenReader)
        }
        ImageExportStatus()
        DocxConversionStatus(navigation.onOpenReader)
        LibraryList(state, actions, batch, { picker.launch(ImportMimeTypes) }, viewModel::retry)
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    actions: DocumentActions,
    batch: BatchState,
    onImport: () -> Unit,
    onRetry: () -> Unit,
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
        state.load == LibraryLoad.Failed -> ErrorRetry(stringResource(R.string.library_load_error), onRetry)
        state.documents.isEmpty() -> LibraryEmpty(state, onImport)
        else -> DocumentList(state.documents, actions, batch, hiltViewModel())
    }
}

@Composable
private fun LibraryEmpty(state: LibraryUiState, onImport: () -> Unit) {
    val searching = state.query.isNotBlank() || state.selectedFolderId != null
    val emptyRes = when {
        state.totalCount == 0 -> R.string.library_empty
        searching -> R.string.search_empty
        else -> state.filter.emptyRes()
    }
    val cta = if (state.totalCount == 0) stringResource(R.string.library_import) else null
    EmptyState(stringResource(emptyRes), cta, onImport)
}

/** What the header shows, grouped so [LibraryHeader] stays under the parameter-count limit. */
private class HeaderInfo(val title: String, val count: Int, val isImporting: Boolean, val isScanTab: Boolean)

@Composable
private fun LibraryHeader(info: HeaderInfo, navigation: LibraryNavigation, onImport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                info.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            CountPill(info.count)
        }
        Button(onClick = onImport, enabled = !info.isImporting, modifier = Modifier.height(44.dp)) {
            Icon(DownloadGlyph, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_import))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_trash)) },
                    onClick = {
                        menuOpen = false
                        navigation.onOpenTrash()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_storage)) },
                    onClick = {
                        menuOpen = false
                        navigation.onOpenStorage()
                    },
                )
                if (info.isScanTab) {
                    HorizontalDivider()
                    ScanQualityItems(onDismiss = { menuOpen = false })
                }
            }
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DocumentList(docs: List<Document>, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(docs, key = { it.id }) { doc -> DocumentCard(doc, actions, batch, covers) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentCard(doc: Document, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else actions.onOpen(doc) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
    ) {
        Box {
            CoverImage(doc, covers, Modifier.fillMaxWidth().height(COVER_HEIGHT.dp))
            if (doc.isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (batch.active) {
                Checkbox(
                    checked = doc.id in batch.selected,
                    onCheckedChange = { batch.toggle(doc.id) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AuthorLine(doc, covers)
                Text(
                    documentMeta(context, doc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!batch.active) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doc_more))
                    }
                    DocumentMenu(menuOpen, doc, actions, export) { menuOpen = false }
                }
            }
        }
    }
}

private const val COVER_HEIGHT = 176

@Composable
private fun DocumentMenu(
    expanded: Boolean,
    doc: Document,
    actions: DocumentActions,
    export: DocumentExport,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(favoriteLabel(doc.isFavorite))) },
            onClick = {
                onDismiss()
                actions.onToggleFavorite(doc)
            },
        )
        listOfNotNull(
            R.string.doc_share to export.share,
            R.string.doc_save_copy to export.saveCopy,
            export.print?.let { R.string.doc_print to it },
            export.exportImages?.let { R.string.doc_export_images to it },
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
        listOf(
            R.string.doc_rename to actions.onRename,
            R.string.doc_move to actions.onMove,
            R.string.doc_tags to actions.onTags,
            R.string.doc_trash to actions.onTrash,
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    action(doc)
                },
            )
        }
    }
}
