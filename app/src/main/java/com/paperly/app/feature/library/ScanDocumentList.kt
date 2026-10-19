package com.paperly.app.feature.library

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.scanner.LocalAddScanPages

private val RowCoverWidth = 64.dp
private val RowCoverHeight = 88.dp

/** Scanned tab: one document per row (cover, name on up to two lines, date, size), so names are not cut off. */
@Composable
internal fun ScanDocumentList(
    docs: List<Document>,
    actions: DocumentActions,
    batch: BatchState,
    covers: CoverViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(docs, key = { it.id }) { doc -> ScanDocumentRow(doc, actions, batch, covers) }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScanDocumentRow(doc: Document, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else actions.onOpen(doc) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CoverImage(
                doc,
                covers,
                Modifier.size(RowCoverWidth, RowCoverHeight),
                cornerRadius = 10.dp,
                letterStyle = MaterialTheme.typography.titleLarge,
            )
            if (doc.isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (batch.active) {
                Checkbox(
                    checked = doc.id in batch.selected,
                    onCheckedChange = { batch.toggle(doc.id) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
        ScanRowText(doc, Modifier.weight(1f))
        if (!batch.active) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doc_more))
                }
                ScanDocumentMenu(menuOpen, doc, actions, export) { menuOpen = false }
            }
        }
    }
}

@Composable
private fun ScanRowText(doc: Document, modifier: Modifier) {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    Column(modifier) {
        Text(
            doc.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            DateUtils.formatDateTime(context, doc.createdAt, flags),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Formatter.formatShortFileSize(context, doc.sizeBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Card menu of a scanned document: the six everyday actions first, the rest under "More" (nothing removed).
 * "Add pages" opens the page editor and starts the scanner there.
 */
@Composable
internal fun ScanDocumentMenu(
    expanded: Boolean,
    doc: Document,
    actions: DocumentActions,
    export: DocumentExport,
    onDismiss: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    val addPages = LocalAddScanPages.current
    val close = {
        more = false
        onDismiss()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = close) {
        if (more) {
            ScanMoreItems(doc, actions, export, close) { more = false }
        } else {
            MenuEntry(R.string.doc_share, close, export.share)
            MenuEntry(R.string.doc_save_copy, close, export.saveCopy)
            MenuEntry(R.string.doc_rename, close) { actions.onRename(doc) }
            MenuEntry(R.string.doc_move, close) { actions.onMove(doc) }
            MenuEntry(R.string.doc_add_pages, close) { addPages(doc.id) }
            MenuEntry(R.string.doc_trash, close) { actions.onTrash(doc) }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doc_menu_more)) },
                onClick = { more = true },
            )
        }
    }
}

@Composable
private fun ScanMoreItems(
    doc: Document,
    actions: DocumentActions,
    export: DocumentExport,
    close: () -> Unit,
    onBack: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(R.string.doc_menu_back)) }, onClick = onBack)
    MenuEntry(favoriteLabel(doc.isFavorite), close) { actions.onToggleFavorite(doc) }
    export.print?.let { MenuEntry(R.string.doc_print, close, it) }
    export.exportImages?.let { MenuEntry(R.string.doc_export_images, close, it) }
    MenuEntry(R.string.doc_tags, close) { actions.onTags(doc) }
}

@Composable
private fun MenuEntry(label: Int, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            close()
            action()
        },
    )
}
