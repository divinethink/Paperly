package com.paperly.app.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.feature.common.documentMeta

private sealed interface Confirm {
    data object EmptyAll : Confirm
    data class One(val doc: Document) : Confirm
}

@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val failed by viewModel.deleteFailed.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    confirm?.let { c ->
        val message = when (c) {
            Confirm.EmptyAll -> stringResource(R.string.trash_confirm_empty, docs.size)
            is Confirm.One -> stringResource(R.string.trash_confirm_one, c.doc.title)
        }
        ConfirmDialog(
            message = message,
            onConfirm = {
                when (c) {
                    Confirm.EmptyAll -> viewModel.emptyTrash()
                    is Confirm.One -> viewModel.deleteForever(c.doc)
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TrashTopBar(onBack, emptyEnabled = docs.isNotEmpty(), onEmpty = { confirm = Confirm.EmptyAll })
        if (failed) {
            Text(
                stringResource(R.string.trash_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(docs, key = { it.id }) { doc ->
                    TrashRow(doc, onRestore = { viewModel.restore(doc) }, onDelete = { confirm = Confirm.One(doc) })
                }
            }
        }
    }
}

@Composable
private fun TrashTopBar(onBack: () -> Unit, emptyEnabled: Boolean, onEmpty: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
        }
        Text(
            stringResource(R.string.trash_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onEmpty, enabled = emptyEnabled) { Text(stringResource(R.string.trash_empty_action)) }
    }
}

@Composable
private fun TrashRow(doc: Document, onRestore: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                doc.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(documentMeta(context, doc), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.trash_restore)) }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.trash_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_confirm_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_confirm_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
