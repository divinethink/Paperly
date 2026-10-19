package com.paperly.app.feature.scanner

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paperly.app.R

private const val THUMB_WIDTH_PX = 360
private const val GRID_COLUMNS = 3 // 6-9 pages on one phone screen
private const val MIN_THUMB_ASPECT = 0.7f // very tall/wide pages are fitted into a box of normal proportions
private const val MAX_THUMB_ASPECT = 1f

/** "Today" / "Yesterday" / a short date; one value for the whole document (pages have no date of their own). */
@Composable
internal fun rememberScanDate(createdAt: Long): String = remember(createdAt) {
    DateUtils.getRelativeTimeSpanString(createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
}

/** Three-column page grid (Notebloc style): page thumbnail with a teal footer — page number left, date right. */
@Composable
internal fun ScanPageGrid(
    ready: ScanPagesUi.Ready,
    viewModel: ScanPagesViewModel,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = rememberScanDate(ready.document.createdAt)
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(ready.pageCount, key = { it }) { index ->
            PageCell(index, ready, date, viewModel) { onPage(index) }
        }
    }
}

@Composable
private fun PageCell(
    index: Int,
    ready: ScanPagesUi.Ready,
    date: String,
    viewModel: ScanPagesViewModel,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.scan_page_desc, index + 1)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        PageThumb(index, ready, viewModel, Modifier.fillMaxWidth())
        PageFooter(index + 1, date)
    }
}

/** Page thumbnail; reloaded when the document was read again ([ScanPagesUi.Ready.revision]). */
@Composable
internal fun PageThumb(index: Int, ready: ScanPagesUi.Ready, viewModel: ScanPagesViewModel, modifier: Modifier) {
    var bitmap by remember(index, ready.revision) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, ready.revision) { bitmap = viewModel.thumbnail(index, THUMB_WIDTH_PX) }
    // The grey box is the loading skeleton.
    val aspect = ready.aspect.coerceIn(MIN_THUMB_ASPECT, MAX_THUMB_ASPECT)
    Box(modifier.aspectRatio(aspect).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PageFooter(number: Int, date: String) {
    val onBar = MaterialTheme.colorScheme.onPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.scan_page_number, number),
            color = onBar,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.border(1.dp, onBar).padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(date, color = onBar, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
