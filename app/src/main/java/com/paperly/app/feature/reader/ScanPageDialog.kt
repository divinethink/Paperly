package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.scanner.MAX_PAGE_NOTE
import com.paperly.app.domain.scanner.MAX_PAGE_TITLE
import com.paperly.app.domain.scanner.ScanPageInfo

/** Edit title + note of the current scanned page (blank both = cleared). [page] is 0-based; shown 1-based. */
@Composable
fun ScanPageDialog(page: Int, initial: ScanPageInfo, onSave: (ScanPageInfo) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var note by remember { mutableStateOf(initial.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_page_title, page + 1)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_PAGE_TITLE) },
                    label = { Text(stringResource(R.string.scan_page_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_PAGE_NOTE) },
                    label = { Text(stringResource(R.string.scan_page_note)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ScanPageInfo(title, note)) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/** Loads the saved title/note first (so the fields start filled), then shows [ScanPageDialog]. */
@Composable
fun ScanPageEditor(page: Int, viewModel: ScanPageViewModel, onClose: () -> Unit) {
    val info: ScanPageInfo? by viewModel.observe(page).collectAsStateWithLifecycle(initialValue = null)
    info?.let {
        ScanPageDialog(
            page = page,
            initial = it,
            onSave = { edited ->
                viewModel.save(page, edited)
                onClose()
            },
            onDismiss = onClose,
        )
    }
}
