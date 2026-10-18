package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.BookmarkGlyph
import com.paperly.app.core.ui.theme.TrashGreen
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val COLLAPSED_ROTATION = -90f
private const val NESTED_INDENT_DP = 42
private const val NESTED_STEP_DP = 16
private const val SCROLL_AFTER_ROWS = 4

/** Per-row display facts. [open]: null = no children, else whether they are shown. */
private class TocRowState(
    val number: Int?,
    val read: Boolean,
    val current: Boolean,
    val bookmarked: Boolean,
    val open: Boolean?,
    val percent: Int,
)

/** Contents tab: "You are here" card, then numbered rows; opens scrolled to the current chapter. */
@Composable
internal fun TocList(entries: List<TocEntry>, viewModel: EpubReaderViewModel, onSelect: (TocEntry) -> Unit) {
    if (entries.isEmpty()) return EmptyHint(R.string.epub_empty_toc)
    val position by viewModel.position.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val publication = viewModel.uiState.collectAsStateWithLifecycle().value.publication
    val order = remember(publication) { publication?.readingOrder?.map { hrefKey(it.url().toString()) }.orEmpty() }
    val numbers = remember(entries) { chapterNumbers(entries) }
    val current = remember(entries, position?.href) {
        currentTocIndex(entries, order, position?.href?.toString()?.let(::hrefKey))
    }
    val marked = remember(bookmarks) {
        bookmarks.mapNotNull { decodeLocator(it)?.href?.toString()?.let(::hrefKey) }.toSet()
    }
    var expanded by remember(entries) { mutableStateOf(tocAncestors(entries, current)) }
    val rows = remember(entries, expanded) { visibleToc(entries, expanded) }
    val listState = rememberLazyListState()
    LaunchedEffect(entries) {
        val at = rows.indexOf(current)
        if (at > SCROLL_AFTER_ROWS) listState.scrollToItem(at)
    }
    val progress = (position?.locations?.progression ?: 0.0).toFloat().coerceIn(0f, 1f)
    LazyColumn(Modifier.fillMaxWidth(), listState, contentPadding = PaddingValues(16.dp)) {
        if (current >= 0) {
            item { YouAreHere(entries[current].title, progress) { onSelect(entries[current]) } }
        }
        items(rows) { index ->
            val entry = entries[index]
            val hasChildren = (entries.getOrNull(index + 1)?.depth ?: 0) > entry.depth
            val state = TocRowState(
                number = numbers[index],
                read = current >= 0 && index < current && numbers[index] != null,
                current = index == current,
                bookmarked = entry.key() in marked,
                open = (index in expanded).takeIf { hasChildren },
                percent = (progress * CONTENTS_PERCENT).roundToInt(),
            )
            TocRow(entry, state, { onSelect(entry) }) {
                expanded = if (index in expanded) expanded - index else expanded + index
            }
        }
    }
}

@Composable
private fun YouAreHere(title: String, progress: Float, onGo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                stringResource(R.string.epub_you_are_here).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                title,
                Modifier.padding(vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
        Button(onClick = onGo, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.epub_go), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TocRow(entry: TocEntry, state: TocRowState, onSelect: () -> Unit, onToggle: () -> Unit) {
    val nested = entry.depth > 0
    val tonal = if (state.current) {
        Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier
    }
    Column {
        Row(
            tonal.fillMaxWidth().heightIn(min = if (nested) 48.dp else 56.dp).clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nested) {
                Spacer(Modifier.width((NESTED_INDENT_DP + (entry.depth - 1) * NESTED_STEP_DP).dp))
            } else {
                NumberDot(state, Modifier.padding(horizontal = 12.dp))
            }
            Text(
                entry.title,
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = if (nested) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (state.number == null && !nested) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (state.current) FontWeight.Bold else FontWeight.Normal,
            )
            RowTrail(state, onToggle)
        }
        if (!state.current) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Left circle: chapter number ("—" for front matter); the current chapter's circle is brown. */
@Composable
private fun NumberDot(state: TocRowState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(28.dp).clip(CircleShape).background(if (state.current) colors.primary else colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            state.number?.let { NumberFormat.getInstance().format(it) } ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (state.current) colors.onPrimary else colors.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Right side: chapter % (current), bookmark icon, green tick (read) and the collapse arrow for parents. */
@Composable
private fun RowTrail(state: TocRowState, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        state.current -> Text(
            "${state.percent}%",
            Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
        )
        state.bookmarked -> Icon(
            BookmarkGlyph,
            stringResource(R.string.epub_chapter_bookmarked),
            Modifier.padding(end = 12.dp).size(22.dp),
            tint = colors.primary,
        )
        state.read -> Icon(
            Icons.Filled.Check,
            stringResource(R.string.epub_chapter_read),
            Modifier.padding(end = 12.dp),
            tint = TrashGreen,
        )
    }
    state.open?.let { open ->
        IconButton(onClick = onToggle) {
            val label = if (open) R.string.epub_toc_collapse else R.string.epub_toc_expand
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(label),
                modifier = Modifier.rotate(if (open) 0f else COLLAPSED_ROTATION),
            )
        }
    }
}
