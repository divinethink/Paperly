package com.paperly.app.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.LibraryLayout
import com.paperly.app.feature.common.documentMeta

private const val GRID_COLUMNS = 3
private val GridCoverHeight = 120.dp
private val RowCoverWidth = 56.dp
private val RowCoverHeight = 76.dp

/** PDF / EPUB tabs: small 3-column covers or one row per document (the choice is saved in the sort menu). */
@Composable
internal fun DocumentList(state: LibraryUiState, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    if (state.view.layout == LibraryLayout.LIST) {
        DocumentRows(state, actions, batch, covers)
    } else {
        DocumentGrid(state, actions, batch, covers)
    }
}

@Composable
private fun DocumentGrid(state: LibraryUiState, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.documents, key = { it.id }) { doc ->
            GridCard(doc, doc.id in state.finishedIds, actions, batch, covers)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GridCard(
    doc: Document,
    finished: Boolean,
    actions: DocumentActions,
    batch: BatchState,
    covers: CoverViewModel,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            role = Role.Button,
            onClick = { if (batch.active) batch.toggle(doc.id) else actions.onOpen(doc) },
            onLongClickLabel = stringResource(R.string.batch_select),
            onLongClick = { batch.toggle(doc.id) },
        ),
    ) {
        Box {
            CoverImage(
                doc,
                covers,
                Modifier.fillMaxWidth().height(GridCoverHeight),
                cornerRadius = 10.dp,
                letterStyle = MaterialTheme.typography.headlineMedium,
            )
            CoverBadges(doc, finished, batch)
            if (!batch.active) CardMenu(doc, actions, Modifier.align(Alignment.BottomEnd), onCover = true)
        }
        Text(
            doc.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
        )
        Text(
            documentMeta(context, doc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** Overlays on a grid cover: Word badge or selection box (top-left), star (top-right), finished check (bottom-left). */
@Composable
private fun BoxScope.CoverBadges(doc: Document, finished: Boolean, batch: BatchState) {
    if (batch.active) {
        Checkbox(
            checked = doc.id in batch.selected,
            onCheckedChange = { batch.toggle(doc.id) },
            modifier = Modifier.align(Alignment.TopStart),
        )
    } else if (doc.convertedFrom != null) {
        DocBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
    if (doc.isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(4.dp), compact = true)
    if (finished) FinishedBadge(Modifier.align(Alignment.BottomStart).padding(6.dp))
}

@Composable
private fun DocumentRows(state: LibraryUiState, actions: DocumentActions, batch: BatchState, covers: CoverViewModel) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(state.documents, key = { it.id }) { doc ->
            SwipeRow(doc, actions, batch) { DocumentRow(doc, doc.id in state.finishedIds, actions, batch, covers) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Swipe right = favourite on/off, swipe left = Trash (it can be restored). The row always springs back. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeRow(doc: Document, actions: DocumentActions, batch: BatchState, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(doc) // the state outlives recompositions, so it must not keep an old copy
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.onToggleFavorite(current)
                SwipeToDismissBoxValue.EndToStart -> actions.onTrash(current)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        gesturesEnabled = !batch.active,
        backgroundContent = { SwipeBackground(state.dismissDirection) },
        content = { content() },
    )
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val scheme = MaterialTheme.colorScheme
    val favorite = direction == SwipeToDismissBoxValue.StartToEnd
    val trash = direction == SwipeToDismissBoxValue.EndToStart
    val color = when {
        favorite -> scheme.primaryContainer
        trash -> scheme.errorContainer
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
        contentAlignment = if (trash) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (favorite) {
            Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.doc_favorite_add))
        } else if (trash) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.doc_trash))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DocumentRow(
    doc: Document,
    finished: Boolean,
    actions: DocumentActions,
    batch: BatchState,
    covers: CoverViewModel,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(
                role = Role.Button,
                onClick = { if (batch.active) batch.toggle(doc.id) else actions.onOpen(doc) },
                onLongClickLabel = stringResource(R.string.batch_select),
                onLongClick = { batch.toggle(doc.id) },
            ).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CoverImage(
                    doc,
                    covers,
                    Modifier.size(RowCoverWidth, RowCoverHeight),
                    cornerRadius = 8.dp,
                    letterStyle = MaterialTheme.typography.titleLarge,
                )
                if (batch.active) {
                    Checkbox(
                        checked = doc.id in batch.selected,
                        onCheckedChange = { batch.toggle(doc.id) },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
            RowTexts(doc, Modifier.weight(1f))
            if (finished) FinishedBadge()
            if (doc.isFavorite) FavoriteBadge(compact = true)
            if (!batch.active) CardMenu(doc, actions)
        }
    }
}

@Composable
private fun RowTexts(doc: Document, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Text(
            doc.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (doc.convertedFrom != null) DocBadge()
            Text(
                documentMeta(context, doc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
