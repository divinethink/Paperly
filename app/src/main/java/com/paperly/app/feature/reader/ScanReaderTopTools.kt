package com.paperly.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.scanner.ScanPageInfo
import com.paperly.app.feature.scanner.LocalOpenScanEditorAt
import com.paperly.app.feature.scanner.ScanEditIcons
import com.paperly.app.feature.scanner.ScanEditStart

/**
 * Reader top bar of a scanned document: the shown page's title (tap = rename) and, when the page editor is
 * available, Crop and Delete for that page. Crop and Delete open the page editor on this page.
 */
@Composable
internal fun RowScope.ScanTitleAndTools(scanPages: ScanPageViewModel, page: Int) {
    val flow = remember(page) { scanPages.observe(page) }
    val info: ScanPageInfo? by flow.collectAsStateWithLifecycle(initialValue = null)
    val canEdit by scanPages.canEditPages.collectAsStateWithLifecycle()
    val openAt = LocalOpenScanEditorAt.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (renaming) ScanPageEditor(page, scanPages) { renaming = false }
    if (deleting) {
        DeletePageDialog(
            onDelete = {
                deleting = false
                openAt(scanPages.documentId, page, ScanEditStart.DELETE)
            },
            onCancel = { deleting = false },
        )
    }
    val title = info?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.scan_page_title, page + 1)
    Text(
        title,
        Modifier
            .weight(1f)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.scan_page_edit)) { renaming = true }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (canEdit) {
        IconButton(onClick = { openAt(scanPages.documentId, page, ScanEditStart.CROP) }) {
            Icon(ScanEditIcons.Crop, contentDescription = stringResource(R.string.scan_reader_crop))
        }
        IconButton(onClick = { deleting = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.scan_reader_delete))
        }
    }
}

@Composable
private fun DeletePageDialog(onDelete: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.scan_edit_delete_title)) },
        text = { Text(stringResource(R.string.scan_reader_delete_body)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.scan_edit_delete)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}
