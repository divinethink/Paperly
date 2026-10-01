package com.paperly.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R

/** Chapter list (P3-D). Tapping an entry jumps there; touch rows are >= 48dp. */
@Composable
internal fun EpubTocDialog(entries: List<TocEntry>, onSelect: (TocEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.epub_toc)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.epub_done)) } },
        text = {
            LazyColumn {
                items(entries) { entry ->
                    Text(
                        text = entry.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(entry) }
                            .padding(start = (entry.depth * INDENT_DP).dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        },
    )
}

private const val INDENT_DP = 16
