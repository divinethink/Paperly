package com.paperly.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.ChevronGlyph
import com.paperly.app.core.ui.icons.FolderGlyph
import com.paperly.app.domain.folder.FOLDER_PATH_SEPARATOR
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

/** The folder one level up from the open one (null = top level). Back on the Scanned tab goes here. */
internal fun LibraryUiState.parentFolderId(): String? = folderPath.getOrNull(folderPath.size - 2)?.id

/**
 * PDF/EPUB tabs keep the folder chip row. The Scanned tab shows folders as rows in the list and, inside a
 * folder, a bar with Back (one level up), the folder path and its menu, plus the "Scan here" buttons.
 */
@Composable
internal fun FolderSection(isScanTab: Boolean, state: LibraryUiState, ui: FolderUi) {
    if (!isScanTab) {
        FolderRow(state.folders, state.selectedFolderId, ui.onSelect, ui.onNew, ui.onDelete)
        return
    }
    if (state.openFolder() == null) return
    ScanFolderBar(state.folderPath, state.parentFolderId(), ui)
    ScanHereRow(ui.onScan, ui.onGallery)
}

@Composable
private fun ScanFolderBar(path: List<Folder>, parentId: String?, ui: FolderUi) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { ui.onSelect(parentId) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.folder_back))
        }
        FolderTitle(path, Modifier.weight(1f))
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

/** Current folder name, with the folders above it ("Bills ›") in a small line when it is nested. */
@Composable
private fun FolderTitle(path: List<Folder>, modifier: Modifier) {
    Column(modifier) {
        if (path.size > 1) {
            val above = path.dropLast(1).joinToString(FOLDER_PATH_SEPARATOR) { it.name } +
                FOLDER_PATH_SEPARATOR.trimEnd()
            Text(
                above,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            path.lastOrNull()?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderMenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

/** One folder in the Scanned list: brown icon tile, name, "N documents" and a chevron (clearly not a document). */
@Composable
internal fun ScanFolderRow(item: ScanFolderItem, onOpen: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().clickable(role = Role.Button) { onOpen(item.id) },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        FolderGlyph,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.scan_folder_documents, item.count, item.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                ChevronGlyph,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
