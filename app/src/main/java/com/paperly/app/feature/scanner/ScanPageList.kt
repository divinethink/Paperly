package com.paperly.app.feature.scanner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private val ListThumbWidth = 64.dp

/** One page per row: small thumbnail, "Page N" and the date. Same pages and taps as the grid. */
@Composable
internal fun ScanPageList(
    ready: ScanPagesUi.Ready,
    viewModel: ScanPagesViewModel,
    chosen: Set<Int>,
    taps: PageTaps,
    modifier: Modifier = Modifier,
) {
    val stampOf = rememberPageStamp(ready, viewModel, chosen)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ready.pageCount, key = { it }) { index ->
            PageRow(index, ready, stampOf(index), viewModel, taps)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageRow(
    index: Int,
    ready: ScanPagesUi.Ready,
    stamp: PageStamp,
    viewModel: ScanPagesViewModel,
    taps: PageTaps,
) {
    val title = rememberPageTitle(viewModel, index)
    val desc = stringResource(R.string.scan_page_desc, index + 1)
    val label = title.ifBlank { desc }
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .pageSemantics(label, stamp.checked)
            .combinedClickable(
                role = Role.Button,
                onLongClick = { taps.onLongPress(index) },
                onClick = { taps.onTap(index) },
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageThumb(index, ready, viewModel, Modifier.width(ListThumbWidth))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(stamp.date, stamp.size).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        stamp.checked?.let { SelectBox(it) }
    }
}
