package com.paperly.app.feature.library

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.scanner.LocalAddScanPages
import com.paperly.app.feature.scanner.LocalOpenScanPages

private val RowCoverWidth = 72.dp
private val RowCoverHeight = 96.dp
private val StackStep = 4.dp

/** Scanned tab: one document per row (cover, name on up to two lines, date, size), so names are not cut off. */
@Composable
internal fun ScanDocumentList(
    state: LibraryUiState,
    actions: DocumentActions,
    batch: BatchState,
    covers: CoverViewModel,
    onOpenFolder: (String) -> Unit,
) {
    // Section labels only when both kinds are on screen; with just one kind they would be clutter.
    val labels = state.scanFolders.isNotEmpty() && state.documents.isNotEmpty()
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (labels) item(key = "label:folders") { SectionLabel(R.string.scan_section_folders) }
        items(state.scanFolders, key = { "folder:" + it.id }) { ScanFolderRow(it, onOpenFolder) }
        if (labels) item(key = "label:documents") { SectionLabel(R.string.scan_section_documents) }
        items(state.documents, key = { it.id }) { doc ->
            ScanDocumentRow(doc, actions, batch, covers)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScanDocumentRow(doc: Document, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    val openPages = LocalOpenScanPages.current // a scanned document opens on its page grid
    var pages by remember(doc.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(doc.id, doc.sizeBytes) { pages = covers.pageCount(doc.id) } // read when shown, never stored
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else openPages(doc.id) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanCoverStack(doc, pages, covers, batch)
        ScanRowText(doc, pages, Modifier.weight(1f))
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

/** Cover with paper-stack layers behind it for a multi-page scan, a page-count chip, favorite star, checkbox. */
@Composable
private fun ScanCoverStack(doc: Document, pages: Int?, covers: CoverViewModel, batch: BatchState) {
    val layers = scanStackLayers(pages)
    Box(Modifier.size(RowCoverWidth + StackStep * 2, RowCoverHeight + StackStep * 2)) {
        for (i in layers downTo 1) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.offset(StackStep * i, StackStep * i).size(RowCoverWidth, RowCoverHeight),
            ) {}
        }
        Box(Modifier.size(RowCoverWidth, RowCoverHeight)) {
            CoverImage(
                doc,
                covers,
                Modifier.fillMaxSize(),
                cornerRadius = 4.dp,
                letterStyle = MaterialTheme.typography.titleLarge,
            )
            if (pages != null && pages > 0) PageCountChip(pages, Modifier.align(Alignment.BottomStart).padding(4.dp))
            if (doc.isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (batch.active) {
                Checkbox(
                    checked = doc.id in batch.selected,
                    onCheckedChange = { batch.toggle(doc.id) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

/** Decorative: the row text already says "N pages". */
@Composable
private fun PageCountChip(pages: Int, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.clearAndSetSemantics {},
    ) {
        Text(
            pages.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SectionLabel(label: Int) {
    Text(
        stringResource(label).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun ScanRowText(doc: Document, pages: Int?, modifier: Modifier) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, doc.sizeBytes)
    val detail = if (pages != null && pages > 0) {
        stringResource(
            R.string.scan_row_pages_size,
            pluralStringResource(R.plurals.scan_doc_pages, pages, pages),
            size,
        )
    } else {
        size
    }
    Column(modifier) {
        Text(
            doc.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            scanRowDate(context, doc.createdAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Today, 1:04 PM", "Yesterday, 8:11 AM", otherwise the short date and time. */
private fun scanRowDate(context: Context, createdAt: Long): String {
    val time = DateUtils.formatDateTime(context, createdAt, DateUtils.FORMAT_SHOW_TIME)
    return when {
        DateUtils.isToday(createdAt) -> context.getString(R.string.scan_row_today, time)
        DateUtils.isToday(createdAt + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.scan_row_yesterday, time)
        else -> DateUtils.formatDateTime(
            context,
            createdAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
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
