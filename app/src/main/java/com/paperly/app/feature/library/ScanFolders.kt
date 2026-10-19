package com.paperly.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.FolderGlyph
import com.paperly.app.domain.folder.Folder

/** Folder actions of the Library screen, grouped so the composables stay under the parameter-count limit. */
internal class FolderUi(
    val onSelect: (String?) -> Unit,
    val onNew: () -> Unit,
    val onRename: () -> Unit,
    val onDelete: (withDocuments: Boolean) -> Unit,
    val onScan: () -> Unit,
    val onGallery: () -> Unit,
)

/** Library list callbacks (import, retry, open a folder), grouped for the same reason. */
internal class ListActions(
    val onImport: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenFolder: (String) -> Unit,
)

internal fun LibraryUiState.openFolder(): Folder? = folders.firstOrNull { it.id == selectedFolderId }

/**
 * PDF/EPUB tabs keep the folder chip row. The Scanned tab shows folders as rows in the list (root) and, inside a
 * folder, a bar with Back, the folder name and its menu, plus the "Scan here" buttons.
 */
@Composable
internal fun FolderSection(isScanTab: Boolean, state: LibraryUiState, ui: FolderUi) {
    if (!isScanTab) {
        FolderRow(state.folders, state.selectedFolderId, ui.onSelect, ui.onNew, ui.onDelete)
        return
    }
    val folder = state.openFolder() ?: return
    ScanFolderBar(folder.name, ui)
    ScanHereRow(ui.onScan, ui.onGallery)
}

@Composable
private fun ScanFolderBar(name: String, ui: FolderUi) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { ui.onSelect(null) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.folder_back))
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.folder_menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                FolderMenuItem(R.string.folder_rename) {
                    menuOpen = false
                    ui.onRename()
                }
                FolderMenuItem(R.string.folder_delete) {
                    menuOpen = false
                    ui.onDelete(false)
                }
                FolderMenuItem(R.string.folder_delete_with_docs) {
                    menuOpen = false
                    ui.onDelete(true)
                }
            }
        }
    }
}

@Composable
private fun FolderMenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

/** One folder in the Scanned list: icon, name, number of documents inside. */
@Composable
internal fun ScanFolderRow(item: ScanFolderItem, onOpen: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item.id) },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                FolderGlyph,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Text(
                    item.count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
