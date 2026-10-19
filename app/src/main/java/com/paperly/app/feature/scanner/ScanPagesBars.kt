package com.paperly.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.CameraGlyph
import com.paperly.app.core.ui.icons.GalleryGlyph
import com.paperly.app.core.ui.icons.ShareGlyph
import com.paperly.app.feature.library.DocumentExport

/** The ⋮ button of the title bar; opens [PagesMenu]. */
@Composable
internal fun PagesMenuButton(export: DocumentExport) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doc_more))
        }
        PagesMenu(open, export) { open = false }
    }
}

/** The document's existing share / save / print / export actions (nothing new). */
@Composable
private fun PagesMenu(expanded: Boolean, export: DocumentExport, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
    }
}

/**
 * Bottom teal bar: Add pictures (opens the page editor, which has the picker) · Add scan · Share · More.
 * It paints under the system navigation bar itself.
 */
@Composable
internal fun PagesBottomBar(export: DocumentExport, onPictures: () -> Unit, onScan: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val item = Modifier.weight(1f)
            BarButton(GalleryGlyph, stringResource(R.string.scan_edit_add_gallery), item, onPictures)
            BarButton(CameraGlyph, stringResource(R.string.scan_edit_add_scan), item, onScan)
            BarButton(ShareGlyph, stringResource(R.string.doc_share), item, export.share)
            Box(item) {
                BarButton(Icons.Filled.MoreVert, stringResource(R.string.doc_menu_more), Modifier.fillMaxWidth()) {
                    more = true
                }
                PagesMenu(more, export) { more = false }
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
