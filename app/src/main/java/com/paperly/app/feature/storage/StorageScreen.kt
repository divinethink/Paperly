package com.paperly.app.feature.storage

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.storage.StorageUsage

@StringRes
private fun typeLabelRes(type: String): Int? = when (type) {
    "pdf" -> R.string.type_pdf
    "epub" -> R.string.type_epub
    "scanned-pdf" -> R.string.type_scan
    else -> null
}

@Composable
fun StorageScreen(onBack: () -> Unit, viewModel: StorageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Text(
                stringResource(R.string.storage_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.usage?.let { item { UsageSection(it, context) } }
            item { SectionTitle(R.string.storage_by_type) }
            state.byType.forEach { item { TypeRow(it, context) } }
            item { SectionTitle(R.string.storage_largest) }
            state.largest.forEach { item { SizeRow(it.title, Formatter.formatShortFileSize(context, it.sizeBytes)) } }
            item { SectionTitle(R.string.storage_duplicates) }
            if (state.duplicates.isEmpty()) item { Text(stringResource(R.string.storage_no_duplicates)) }
            state.duplicates.forEach { group -> item { DuplicateCard(group, context, viewModel::keepOnly) } }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SizeRow(label: String, size: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(size, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun UsageSection(u: StorageUsage, context: Context) {
    Column {
        SizeRow(
            pluralStringResource(R.plurals.settings_storage_documents, u.activeCount, u.activeCount),
            Formatter.formatShortFileSize(context, u.activeBytes),
        )
        SizeRow(
            pluralStringResource(R.plurals.settings_storage_trash, u.trashCount, u.trashCount),
            Formatter.formatShortFileSize(context, u.trashBytes),
        )
        SizeRow(stringResource(R.string.settings_storage_total), Formatter.formatShortFileSize(context, u.totalBytes))
    }
}

@Composable
private fun TypeRow(row: TypeSize, context: Context) {
    val name = typeLabelRes(row.type)?.let { stringResource(it) } ?: row.type
    val label = stringResource(R.string.storage_type_row, name, row.count)
    SizeRow(label, Formatter.formatShortFileSize(context, row.bytes))
}

@Composable
private fun DuplicateCard(group: DuplicateGroup, context: Context, onKeep: (DuplicateGroup, String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.storage_dup_group, group.documents.size),
                style = MaterialTheme.typography.labelLarge,
            )
            group.documents.forEach { doc -> DuplicateRow(doc, context) { onKeep(group, doc.id) } }
        }
    }
}

@Composable
private fun DuplicateRow(doc: Document, context: Context, onKeep: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SizeRow(doc.title, Formatter.formatShortFileSize(context, doc.sizeBytes))
        TextButton(onClick = onKeep) { Text(stringResource(R.string.storage_keep_this)) }
    }
}
