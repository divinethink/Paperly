package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

internal const val CONTENTS_PERCENT = 100

private val TAB_LABELS = listOf(R.string.epub_toc, R.string.epub_tab_bookmarks, R.string.epub_tab_annotations)
private const val BOOKMARKS_TAB = 1

/** Contents / Bookmarks / Annotations as a full page from the left (Architecture 11.1); a row tap jumps there. */
@Composable
internal fun EpubContentsPage(
    visible: Boolean,
    toc: List<TocEntry>,
    viewModel: EpubReaderViewModel,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val annotations = viewModel.annotations
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val notes by annotations.items.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val title = viewModel.uiState.collectAsStateWithLifecycle().value.title
    val chapters = remember(toc) { chapterNumbers(toc).count { it != null } }
    fun jump(locator: Locator) {
        EpubFragmentHost.navigator(activity)?.go(locator, animated = false)
        onDismiss()
    }
    fun jumpLink(link: Link) {
        EpubFragmentHost.navigator(activity)?.go(link, animated = false)
        onDismiss()
    }
    SidePanelFrame(visible, fromStart = true, onDismiss = onDismiss) {
        ContentsHeader(title, position, chapters, onDismiss)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            PillSegmented(TAB_LABELS, tab, mapOf(BOOKMARKS_TAB to bookmarks.size)) { tab = it }
        }
        when (tab) {
            0 -> TocList(toc, viewModel) { jumpLink(it.link) }
            1 -> BookmarkList(bookmarks, ::jump, viewModel::removeBookmark)
            else -> {
                ShareEpubAnnotationsButton(notes, title)
                NoteList(notes, ::jump) {
                    annotations.startEdit(it)
                    onDismiss()
                }
            }
        }
    }
}

/** Back + book title + "N% read · M chapters". */
@Composable
private fun ContentsHeader(title: String, position: Locator?, chapters: Int, onDismiss: () -> Unit) {
    val read = ((position?.locations?.totalProgression ?: 0.0) * CONTENTS_PERCENT).roundToInt()
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.epub_close))
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.epub_contents_meta, read, chapters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EmptyHint(text: Int) {
    Text(
        stringResource(text),
        Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
