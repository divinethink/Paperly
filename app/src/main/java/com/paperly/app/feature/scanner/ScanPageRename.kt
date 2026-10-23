package com.paperly.app.feature.scanner

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.scanner.MAX_PAGE_TITLE

/** Saved title of page [index] (0-based) for the grid / list; "" = none. */
@Composable
internal fun rememberPageTitle(viewModel: ScanPagesViewModel, index: Int): String {
    val title by remember(index) { viewModel.pageTitle(index) }.collectAsStateWithLifecycle(initialValue = "")
    return title
}

/** Which page is being renamed from the selection bar, and what to do afterwards. */
internal class PageRename(val index: Int, val viewModel: ScanPagesViewModel, val onDone: () -> Unit)

/** Title-only rename of one page; opens once the saved title is loaded so the field starts filled. */
@Composable
internal fun PageRenameDialog(rename: PageRename, onClose: () -> Unit) {
    val index = rename.index
    val saved: String? by remember(index) { rename.viewModel.pageTitle(index) }
        .collectAsStateWithLifecycle(initialValue = null)
    saved?.let { initial ->
        var title by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.scan_page_title, index + 1)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_PAGE_TITLE) },
                    label = { Text(stringResource(R.string.scan_page_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rename.viewModel.renamePage(index, title)
                        onClose()
                        rename.onDone()
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
