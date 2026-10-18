package com.paperly.app.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.document.LibraryView
import com.paperly.app.domain.folder.Folder

private val FieldHeight = 48.dp
private val ChipHeight = 36.dp
private val SegmentHeight = 40.dp
private val DashLength = 6.dp

@StringRes
private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.filter_all
    LibraryFilter.Favorites -> R.string.filter_favorites
    LibraryFilter.Recent -> R.string.filter_recent
}

/** Search field + sort button on one row. */
@Composable
internal fun SearchRow(query: String, onQueryChange: (String) -> Unit, view: LibraryView) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(query, onQueryChange, Modifier.weight(1f))
        SortButton(view)
    }
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.search_hint)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.height(FieldHeight).semantics { contentDescription = hint },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(FieldHeight).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Icon(Icons.Filled.Search, contentDescription = null, tint = muted)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = muted)
                    }
                    inner()
                }
            }
        },
    )
}

/** All / Favorites / Recent as one segmented control (the selected segment is a light pill). */
@Composable
internal fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryFilter.entries.forEach { filter ->
            Segment(stringResource(filter.labelRes()), filter == selected) { onSelect(filter) }
        }
    }
}

@Composable
private fun RowScope.Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent,
        modifier = Modifier.weight(1f).height(SegmentHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One row of folder chips ("All folders" first, "+ New" last); Delete appears for the selected folder. */
@Composable
internal fun FolderRow(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    onDelete: (withDocuments: Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderChip(stringResource(R.string.folder_all), selectedId == null) { onSelect(null) }
        folders.forEach { folder ->
            FolderChip(folder.name, folder.id == selectedId) { onSelect(folder.id) }
        }
        FolderChip("+ " + stringResource(R.string.folder_new), selected = false, dashed = true, onClick = onNew)
        if (selectedId != null) {
            TextButton(onClick = { onDelete(false) }) { Text(stringResource(R.string.folder_delete)) }
            TextButton(onClick = { onDelete(true) }) { Text(stringResource(R.string.folder_delete_with_docs)) }
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    val borderColor = if (selected) scheme.primary else outline
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
        border = if (dashed) null else BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(ChipHeight),
    ) {
        val dashing = if (dashed) Modifier.drawBehind { drawDashedBorder(outline) } else Modifier
        Box(dashing.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
    }
}

private fun DrawScope.drawDashedBorder(color: Color) {
    val dash = DashLength.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}
