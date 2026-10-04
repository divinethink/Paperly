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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
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
    onOpenReader: (documentId: String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    organize: OrganizeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val batch = rememberBatchState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri == null -> Unit
            isDocx(context, uri) -> enqueueDocxConversion(context, uri) // Word file -> background EPUB conversion
            else -> viewModel.importDocument(uri.toString())
        }
    }
    val actions = DocumentActions(
        onOpen = { onOpenReader(it.id) },
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
                onOpenReader(dup.existingId)
            },
        )
    }
    dialog?.let { LibraryDialogHost(it, state.folders, viewModel, organize) { dialog = null } }

    Column(Modifier.fillMaxSize()) {
        if (batch.active) {
            BatchBar(batch, state.documents, state.folders)
        } else {
            LibraryHeader(state.totalCount, state.isImporting, onOpenTrash, onOpenStorage) {
                picker.launch(ImportMimeTypes)
            }
        }
        SearchField(state.query, viewModel::setQuery)
        FilterRow(state.filter, viewModel::setFilter)
        FolderRow(
            folders = state.folders,
            selectedId = state.selectedFolderId,
            onSelect = viewModel::selectFolder,
            onNew = { dialog = LibraryDialog.NewFolder },
            onDelete = { state.selectedFolderId?.let(organize::deleteFolder) },
        )
        ViewRow(state.view)
        ContinueSection(viewModel.continueReading, state.query, onOpenReader)
        ImageExportStatus()
        DocxConversionStatus(onOpenReader)
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
        else -> DocumentList(state.documents, actions, batch)
    }
}

@Composable
private fun LibraryEmpty(state: LibraryUiState, onImport: () -> Unit) {
    val searching = state.query.isNotBlank() || state.selectedFolderId != null || state.view.type != null
    val emptyRes = when {
        state.totalCount == 0 -> R.string.library_empty
        searching -> R.string.search_empty
        else -> state.filter.emptyRes()
    }
    val cta = if (state.totalCount == 0) stringResource(R.string.library_import) else null
    EmptyState(stringResource(emptyRes), cta, onImport)
}

@Composable
private fun LibraryHeader(
    totalCount: Int,
    isImporting: Boolean,
    onOpenTrash: () -> Unit,
    onOpenStorage: () -> Unit,
    onImport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.library_title, totalCount),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onImport, enabled = !isImporting) {
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
                            onOpenTrash()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_storage)) },
                        onClick = {
                            menuOpen = false
                            onOpenStorage()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentList(docs: List<Document>, actions: DocumentActions, batch: BatchState) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(docs, key = { it.id }) { doc -> DocumentRow(doc, actions, batch) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentRow(doc: Document, actions: DocumentActions, batch: BatchState) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    Card(
        Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else actions.onOpen(doc) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (batch.active) {
                Checkbox(checked = doc.id in batch.selected, onCheckedChange = { batch.toggle(doc.id) })
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(documentMeta(context, doc), style = MaterialTheme.typography.bodySmall)
            }
            if (!batch.active) {
                FavoriteButton(doc.isFavorite) { actions.onToggleFavorite(doc) }
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

@Composable
private fun DocumentMenu(
    expanded: Boolean,
    doc: Document,
    actions: DocumentActions,
    export: DocumentExport,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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

@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    val description = if (isFavorite) R.string.doc_favorite_remove else R.string.doc_favorite_add
    val tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    IconButton(onClick = onToggle) {
        Icon(Icons.Filled.Favorite, contentDescription = stringResource(description), tint = tint)
    }
}
