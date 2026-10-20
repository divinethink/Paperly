package com.paperly.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.document.Document

private const val SCRIM_ALPHA = 0.45f

/** "More" button with the document menu; [onCover] adds a dark round backing (visible on covers). */
@Composable
internal fun CardMenu(
    doc: Document,
    actions: DocumentActions,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val export = rememberDocumentExport(doc)
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.doc_more),
                tint = if (onCover) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onCover) {
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = SCRIM_ALPHA)).padding(2.dp)
                } else {
                    Modifier
                },
            )
        }
        DocumentMenu(open, doc, actions, export) { open = false }
    }
}

@Composable
private fun DocumentMenu(
    expanded: Boolean,
    doc: Document,
    actions: DocumentActions,
    export: DocumentExport,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(favoriteLabel(doc.isFavorite))) },
            onClick = {
                onDismiss()
                actions.onToggleFavorite(doc)
            },
        )
        listOfNotNull(
            R.string.doc_share to export.share,
            R.string.doc_save_copy to export.saveCopy,
            export.print?.let { R.string.doc_print to it },
            export.exportImages?.let { R.string.doc_export_images to it },
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
        listOf(
            R.string.doc_rename to actions.onRename,
            R.string.doc_move to actions.onMove,
            R.string.doc_tags to actions.onTags,
            R.string.doc_trash to actions.onTrash,
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    action(doc)
                },
            )
        }
    }
}
