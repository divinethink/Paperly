package com.paperly.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.EpubAnnotation
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

private const val PERCENT = 100
private const val INDENT_DP = 16

private val TAB_LABELS = listOf(R.string.epub_toc, R.string.epub_tab_bookmarks, R.string.epub_tab_annotations)

/** Contents / Bookmarks / Annotations bottom sheet (Architecture 11.1 tri-tab drawer); a row tap jumps there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubContentsSheet(
    toc: List<TocEntry>,
    bookmarks: List<String>,
    annotations: EpubAnnotationController,
    onRemoveBookmark: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val notes by annotations.items.collectAsStateWithLifecycle()
    fun jump(locator: Locator) {
        EpubFragmentHost.navigator(activity)?.go(locator, animated = false)
        onDismiss()
    }
    fun jumpLink(link: Link) {
        EpubFragmentHost.navigator(activity)?.go(link, animated = false)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            TabRow(selectedTabIndex = tab) {
                TAB_LABELS.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> TocList(toc) { jumpLink(it.link) }
                1 -> BookmarkList(bookmarks, ::jump, onRemoveBookmark)
                else -> NoteList(notes, ::jump) {
                    annotations.startEdit(it)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: Int) {
    Text(stringResource(text), Modifier.fillMaxWidth().padding(24.dp))
}

@Composable
private fun TocList(entries: List<TocEntry>, onSelect: (TocEntry) -> Unit) {
    if (entries.isEmpty()) return EmptyHint(R.string.epub_empty_toc)
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = LIST_MIN_DP.dp)) {
        items(entries) { entry ->
            Text(
                text = entry.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(entry) }
                    .padding(start = (16 + entry.depth * INDENT_DP).dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun BookmarkList(bookmarks: List<String>, onJump: (Locator) -> Unit, onRemove: (String) -> Unit) {
    if (bookmarks.isEmpty()) return EmptyHint(R.string.epub_empty_bookmarks)
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = LIST_MIN_DP.dp)) {
        items(bookmarks) { json ->
            val locator = decodeLocator(json)
            val percent = ((locator?.locations?.totalProgression ?: locator?.locations?.progression ?: 0.0) * PERCENT)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                val title = locator?.title?.takeIf { it.isNotBlank() } ?: locator?.href?.toString().orEmpty()
                Text(
                    "$title · ${percent.roundToInt()}%",
                    Modifier
                        .weight(1f)
                        .clickable(enabled = locator != null) { locator?.let(onJump) }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemove(json) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.reader_annotation_delete))
                }
            }
        }
    }
}

@Composable
private fun NoteList(notes: List<EpubAnnotation>, onJump: (Locator) -> Unit, onEdit: (String) -> Unit) {
    if (notes.isEmpty()) return EmptyHint(R.string.epub_empty_annotations)
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = LIST_MIN_DP.dp)) {
        items(notes) { note ->
            val locator = decodeLocator(note.locatorJson)
            val label = note.noteText ?: locator?.text?.highlight.orEmpty()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    Modifier
                        .weight(1f)
                        .clickable(enabled = locator != null) { locator?.let(onJump) }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onEdit(note.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_annotation_edit))
                }
            }
        }
    }
}

private const val LIST_MIN_DP = 240
