package com.paperly.app.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.scanner.LocalOpenScanPages

/** Columns of every Grid view (Scanned, PDF, EPUB): small covers, 4 per row. */
internal const val LIBRARY_GRID_COLUMNS = 4
private const val MENU_BUTTON_ALPHA = 0.85f
private val ScanGridCoverHeight = 100.dp
private val GridMenuButton = 30.dp
private val ScanStackStep = 4.dp

/** Scanned tab, Grid view: small covers in four columns, folders are tiles in the same grid. Tap opens pages. */
@Composable
internal fun ScanDocumentGrid(
    state: LibraryUiState,
    actions: DocumentActions,
    batch: BatchState,
    covers: CoverViewModel,
    onOpenFolder: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(LIBRARY_GRID_COLUMNS),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.scanFolders, key = { "folder:" + it.id }) {
            ScanFolderCell(it, ScanGridCoverHeight + ScanStackStep * 2, onOpenFolder)
        }
        // Folders and documents never share a row: a full-width empty item ends the folder row.
        if (state.scanFolders.isNotEmpty() && state.documents.isNotEmpty()) {
            item(key = "folders-end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(0.dp)) }
        }
        items(state.documents, key = { it.id }) { doc -> ScanGridCell(doc, actions, batch, covers) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScanGridCell(doc: Document, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    var pages by remember(doc.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(doc.id, doc.sizeBytes) { pages = covers.pageCount(doc.id) } // read when shown, never stored
    val openPages = LocalOpenScanPages.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else openPages(doc.id) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
    ) {
        ScanGridCover(doc, pages, covers, batch, actions)
        Text(
            doc.title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            gridDetail(doc, pages),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun gridDetail(doc: Document, pages: Int?): String {
    val size = Formatter.formatShortFileSize(LocalContext.current, doc.sizeBytes)
    return if (pages != null && pages > 0) {
        stringResource(R.string.scan_row_pages_size, pluralStringResource(R.plurals.scan_doc_pages, pages, pages), size)
    } else {
        size
    }
}

/** Cover with paper-stack layers behind it (multi-page), page-count chip, favorite star, checkbox and the card menu. */
@Composable
private fun ScanGridCover(
    doc: Document,
    pages: Int?,
    covers: CoverViewModel,
    batch: BatchState,
    actions: DocumentActions,
) {
    val layers = scanStackLayers(pages)
    val inset = ScanStackStep * layers
    var menuOpen by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    Box(Modifier.fillMaxWidth().height(ScanGridCoverHeight + inset)) {
        for (i in layers downTo 1) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxSize().padding(
                    start = ScanStackStep * i,
                    top = ScanStackStep * i,
                    end = inset - ScanStackStep * i,
                    bottom = inset - ScanStackStep * i,
                ),
            ) {}
        }
        Box(Modifier.fillMaxSize().padding(end = inset, bottom = inset)) {
            CoverImage(
                doc,
                covers,
                Modifier.fillMaxSize(),
                cornerRadius = 4.dp,
                letterStyle = MaterialTheme.typography.headlineMedium,
            )
            if (pages != null && pages > 0) PageCountChip(pages, Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (doc.isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (batch.active) {
                Checkbox(
                    checked = doc.id in batch.selected,
                    onCheckedChange = { batch.toggle(doc.id) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            } else {
                Box(Modifier.align(Alignment.BottomEnd)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = MENU_BUTTON_ALPHA),
                    ) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(GridMenuButton)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.doc_more),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    ScanDocumentMenu(menuOpen, doc, actions, export) { menuOpen = false }
                }
            }
        }
    }
}
