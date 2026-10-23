package com.paperly.app.feature.scanner

import android.graphics.Bitmap
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

internal const val THUMB_WIDTH_PX = 360
private const val GRID_COLUMNS = 3 // 6-9 pages on one phone screen
private const val MIN_THUMB_ASPECT = 0.7f // very tall/wide pages are fitted into a box of normal proportions
private const val MAX_THUMB_ASPECT = 1f
private const val BOX_ALPHA = 0.8f

/** "Today" / "Yesterday" / a short date; one value for the whole document (pages have no date of their own). */
@Composable
internal fun rememberScanDate(createdAt: Long): String = remember(createdAt) {
    DateUtils.getRelativeTimeSpanString(createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
}

/**
 * What a page shows besides its number: the document's date, the page's stored picture size (null = unknown) and its
 * tick box ([checked] null = not selecting, so no box).
 */
internal data class PageStamp(val date: String, val size: String?, val checked: Boolean? = null)

/** Tap = open/toggle, long press = start selecting (or add to the selection). Pages are 0-based. */
internal class PageTaps(val onTap: (Int) -> Unit, val onLongPress: (Int) -> Unit)

/**
 * Stamp of page [index] (0-based); sizes appear once they are read and only while they match the page count.
 * [chosen] = ticked pages; a non-empty set means selecting, so every page gets a tick box.
 */
@Composable
internal fun rememberPageStamp(
    ready: ScanPagesUi.Ready,
    viewModel: ScanPagesViewModel,
    chosen: Set<Int>,
): (Int) -> PageStamp {
    val date = rememberScanDate(ready.document.createdAt)
    val sizes by viewModel.pageSizes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    return { index ->
        val bytes = sizes.takeIf { it.size == ready.pageCount }?.getOrNull(index)?.takeIf { it > 0L }
        val size = bytes?.let { Formatter.formatShortFileSize(context, it) }
        PageStamp(date, size, if (chosen.isEmpty()) null else index in chosen)
    }
}

/** Three-column page grid (Notebloc style): page thumbnail with a teal footer — page number left, date right. */
@Composable
internal fun ScanPageGrid(
    ready: ScanPagesUi.Ready,
    viewModel: ScanPagesViewModel,
    chosen: Set<Int>,
    taps: PageTaps,
    modifier: Modifier = Modifier,
) {
    val stampOf = rememberPageStamp(ready, viewModel, chosen)
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(ready.pageCount, key = { it }) { index ->
            PageCell(index, ready, stampOf(index), viewModel, taps)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCell(
    index: Int,
    ready: ScanPagesUi.Ready,
    stamp: PageStamp,
    viewModel: ScanPagesViewModel,
    taps: PageTaps,
) {
    val label = stringResource(R.string.scan_page_desc, index + 1)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .pageSemantics(label, stamp.checked)
            .combinedClickable(
                role = Role.Button,
                onLongClick = { taps.onLongPress(index) },
                onClick = { taps.onTap(index) },
            ),
    ) {
        Box {
            PageThumb(index, ready, viewModel, Modifier.fillMaxWidth())
            stamp.checked?.let { SelectBox(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        }
        PageFooter(index + 1, stamp)
    }
}

/** Page description for TalkBack plus "Selected" / "Not selected" while selecting. */
@Composable
internal fun Modifier.pageSemantics(label: String, checked: Boolean?): Modifier {
    val state = checked?.let {
        stringResource(if (it) R.string.scan_page_state_selected else R.string.scan_page_state_not_selected)
    }
    return semantics(mergeDescendants = true) {
        contentDescription = label
        if (state != null) stateDescription = state
    }
}

/** Square tick box in the teal of the theme; the unticked one has a light fill so it shows on any page. */
@Composable
internal fun SelectBox(checked: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val fill = if (checked) color else MaterialTheme.colorScheme.surface.copy(alpha = BOX_ALPHA)
    Box(modifier.size(24.dp).background(fill).border(2.dp, color), contentAlignment = Alignment.Center) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
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
private fun PageFooter(number: Int, stamp: PageStamp) {
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
        Column(horizontalAlignment = Alignment.End) {
            Text(stamp.date, color = onBar, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            stamp.size?.let { Text(it, color = onBar, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}
