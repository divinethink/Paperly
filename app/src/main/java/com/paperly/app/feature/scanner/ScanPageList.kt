package com.paperly.app.feature.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private val ListThumbWidth = 64.dp

/** One page per row: small thumbnail, "Page N" and the date. Same pages and taps as the grid. */
@Composable
internal fun ScanPageList(
    ready: ScanPagesUi.Ready,
    viewModel: ScanPagesViewModel,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stampOf = rememberPageStamp(ready, viewModel)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ready.pageCount, key = { it }) { index ->
            PageRow(index, ready, stampOf(index), viewModel) { onPage(index) }
        }
    }
}

@Composable
private fun PageRow(
    index: Int,
    ready: ScanPagesUi.Ready,
    stamp: PageStamp,
    viewModel: ScanPagesViewModel,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.scan_page_desc, index + 1)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageThumb(index, ready, viewModel, Modifier.width(ListThumbWidth))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(stamp.date, stamp.size).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
