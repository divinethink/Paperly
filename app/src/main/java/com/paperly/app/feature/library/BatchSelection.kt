package com.paperly.app.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.folder.Folder

/** Long-press starts selecting; while any card is selected, taps toggle instead of opening. */
@Stable
internal class BatchState {
    var selected by mutableStateOf(emptySet<String>())
        private set

    val active: Boolean get() = selected.isNotEmpty()

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clear() {
        selected = emptySet()
    }
}

@Composable
internal fun rememberBatchState(): BatchState = remember { BatchState() }

private enum class BatchDialog { Move, Tags }

/** Replaces the Library header while documents are selected. Back clears the selection first. */
@Composable
internal fun BatchBar(
    batch: BatchState,
    docs: List<Document>,
    folders: List<Folder>,
    viewModel: BatchViewModel = hiltViewModel(),
) {
    var dialog by remember { mutableStateOf<BatchDialog?>(null) }
    val chosen = docs.filter { it.id in batch.selected }
    BackHandler { batch.clear() }
    when (dialog) {
        BatchDialog.Move -> MoveFolderDialog(
            currentFolderId = null,
            folders = folders,
            onPick = {
                viewModel.move(chosen, it)
                dialog = null
                batch.clear()
            },
            onDismiss = { dialog = null },
        )
        BatchDialog.Tags -> TagsDialog(
            initial = "",
            onConfirm = {
                viewModel.addTags(chosen, it)
                dialog = null
                batch.clear()
            },
            onDismiss = { dialog = null },
            title = R.string.batch_tags,
        )
        null -> Unit
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.batch_selected, chosen.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
        )
        val enabled = chosen.isNotEmpty()
        TextButton(
            onClick = {
                viewModel.setFavorite(chosen, !chosen.all { it.isFavorite })
                batch.clear()
            },
            enabled = enabled,
        ) { Text(stringResource(R.string.batch_favorite)) }
        TextButton(onClick = { dialog = BatchDialog.Move }, enabled = enabled) {
            Text(stringResource(R.string.batch_move))
        }
        TextButton(onClick = { dialog = BatchDialog.Tags }, enabled = enabled) {
            Text(stringResource(R.string.batch_tags))
        }
        TextButton(
            onClick = {
                viewModel.moveToTrash(chosen)
                batch.clear()
            },
            enabled = enabled,
        ) { Text(stringResource(R.string.batch_trash)) }
        TextButton(onClick = { batch.clear() }) { Text(stringResource(R.string.batch_done)) }
    }
}
