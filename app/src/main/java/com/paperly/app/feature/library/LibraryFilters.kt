package com.paperly.app.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.folder.Folder

@StringRes
private fun LibraryFilter.labelRes(): Int = when (this) {
    LibraryFilter.All -> R.string.filter_all
    LibraryFilter.Favorites -> R.string.filter_favorites
    LibraryFilter.Recent -> R.string.filter_recent
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text(stringResource(R.string.search_hint)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@Composable
internal fun FolderRow(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.folder_all)) },
        )
        folders.forEach { folder ->
            FilterChip(
                selected = folder.id == selectedId,
                onClick = { onSelect(folder.id) },
                label = { Text(folder.name) },
            )
        }
        AssistChip(onClick = onNew, label = { Text(stringResource(R.string.folder_new)) })
        if (selectedId != null) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.folder_delete)) }
        }
    }
}
