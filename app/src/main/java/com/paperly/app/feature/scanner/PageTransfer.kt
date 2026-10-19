package com.paperly.app.feature.scanner

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

private const val DISABLED_ALPHA = 0.38f

/** What the selection bar needs from Move/Copy: whether one is running, and how to start the picker. */
internal class PageTransfer(val busy: Boolean, val start: (TransferMode) -> Unit)

/**
 * Move/Copy of the ticked pages: the target picker, the result message and the running state, with their own
 * ViewModel (keeps the grid screen small). [onDone] runs after a full success; `moved` = the source document changed.
 */
@Composable
internal fun rememberPageTransfer(chosen: Set<Int>, onDone: (moved: Boolean) -> Unit): PageTransfer {
    val viewModel: ScanPageTransferViewModel = hiltViewModel()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<TransferMode?>(null) }
    TransferNoticeEffect(viewModel)
    mode?.let { open ->
        TransferTargetDialog(
            open,
            chosen.size,
            targets,
            onPick = { target ->
                mode = null
                viewModel.start(open, chosen, target, onDone)
            },
            onDismiss = { mode = null },
        )
    }
    return PageTransfer(busy) { next ->
        mode = next
        viewModel.loadTargets(next)
    }
}

/** Target picker: "New document" first, then the scanned documents (greyed when they kept no originals). */
@Composable
private fun TransferTargetDialog(
    mode: TransferMode,
    count: Int,
    targets: List<TransferTarget>?,
    onPick: (documentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val moving = mode == TransferMode.MOVE
    val title = if (moving) R.plurals.scan_transfer_move_title else R.plurals.scan_transfer_copy_title
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(title, count, count)) },
        text = {
            if (targets == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn {
                    item { TargetRow(stringResource(R.string.scan_transfer_new_document), null, true) { onPick(null) } }
                    items(targets, key = { it.documentId }) { row ->
                        val note = if (row.enabled) null else stringResource(R.string.scan_transfer_no_originals)
                        TargetRow(row.title, note, row.enabled) { onPick(row.documentId) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TargetRow(title: String, note: String?, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = DISABLED_ALPHA),
        )
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** Shows how a Move/Copy ended, once. */
@Composable
private fun TransferNoticeEffect(viewModel: ScanPageTransferViewModel) {
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val text = when (notice) {
        TransferNotice.COPIED -> stringResource(R.string.scan_transfer_copied)
        TransferNotice.MOVED -> stringResource(R.string.scan_transfer_moved)
        TransferNotice.NOT_EDITABLE -> stringResource(R.string.scan_transfer_not_editable)
        TransferNotice.MOVE_BLOCKED -> stringResource(R.string.scan_transfer_move_blocked)
        TransferNotice.DUPLICATE -> stringResource(R.string.scan_transfer_duplicate)
        TransferNotice.FAILED -> stringResource(R.string.scan_transfer_failed)
        TransferNotice.PARTIAL_COPY -> stringResource(R.string.scan_transfer_partial_copy)
        TransferNotice.PARTIAL_MOVE -> stringResource(R.string.scan_transfer_partial_move)
        TransferNotice.NOT_REMOVED -> stringResource(R.string.scan_transfer_not_removed)
        null -> null
    }
    LaunchedEffect(notice) {
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            viewModel.dismissNotice()
        }
    }
}
