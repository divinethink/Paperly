package com.paperly.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.paperly.app.R
import com.paperly.app.core.ui.PaperlyTopBar
import com.paperly.app.core.ui.icons.CopyGlyph
import com.paperly.app.core.ui.icons.MoveGlyph
import com.paperly.app.core.ui.icons.ShareGlyph
import com.paperly.app.feature.library.PageExport

/** Selection mode title bar: "N selected", back = leave selection, "Select all". */
@Composable
internal fun PagesSelectTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    PaperlyTopBar(stringResource(R.string.batch_selected, count), onBack = onClose) {
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.scan_pages_select_all)) }
    }
}

/** Selection mode bottom bar: Delete · Move · Copy · Share · More (email, print, Gallery, storage). */
@Composable
internal fun PagesSelectBar(
    count: Int,
    busy: Boolean,
    export: PageExport,
    onTransfer: (TransferMode) -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    if (confirm) {
        ConfirmDeleteDialog(
            count,
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false },
        )
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val item = Modifier.weight(1f)
            BarButton(Icons.Filled.Delete, stringResource(R.string.scan_pages_delete), item) {
                if (!busy) confirm = true
            }
            BarButton(MoveGlyph, stringResource(R.string.scan_pages_move), item) {
                if (!busy) onTransfer(TransferMode.MOVE)
            }
            BarButton(CopyGlyph, stringResource(R.string.scan_pages_copy), item) {
                if (!busy) onTransfer(TransferMode.COPY)
            }
            BarButton(ShareGlyph, stringResource(R.string.doc_share), item, export.share)
            Box(item) {
                BarButton(Icons.Filled.MoreVert, stringResource(R.string.doc_menu_more), Modifier.fillMaxWidth()) {
                    more = true
                }
                SelectMoreMenu(more, export) { more = false }
            }
        }
    }
}

@Composable
private fun SelectMoreMenu(expanded: Boolean, export: PageExport, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            R.string.scan_pages_send_email to export.email,
            R.string.doc_print to export.print,
            R.string.scan_pages_save_gallery to export.gallery,
            R.string.scan_pages_save_storage to export.storage,
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.scan_pages_delete_title, count, count)) },
        text = { Text(stringResource(R.string.scan_pages_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.scan_pages_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
