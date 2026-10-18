package com.paperly.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.BookmarkGlyph
import com.paperly.app.domain.reader.EpubAnnotation
import kotlin.math.roundToInt
import org.readium.r2.shared.publication.Locator

private val CardShape = RoundedCornerShape(20.dp)

/** Bookmarks tab: one card per bookmark (chapter, snippet, position); tapping the card jumps there. */
@Composable
internal fun BookmarkList(bookmarks: List<String>, onJump: (Locator) -> Unit, onRemove: (String) -> Unit) {
    if (bookmarks.isEmpty()) return EmptyHint(R.string.epub_empty_bookmarks)
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(bookmarks) { json ->
            val locator = decodeLocator(json)
            val snippet = locator?.text?.let { it.highlight ?: it.after }?.trim().orEmpty()
            MarkCard(
                onClick = locator?.let { { onJump(it) } },
                leading = {
                    Icon(
                        BookmarkGlyph,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                action = {
                    IconButton(onClick = { onRemove(json) }) {
                        val label = stringResource(R.string.reader_annotation_delete)
                        Icon(Icons.Filled.Delete, contentDescription = label)
                    }
                },
            ) {
                Text(chapterOf(locator), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (snippet.isNotEmpty()) MarkSecondary("\"$snippet…\"")
                MarkSecondary("${percentOf(locator)}%")
            }
        }
    }
}

/** Annotations tab: colour dot + text card; the pencil edits, a tap on the card jumps. */
@Composable
internal fun NoteList(notes: List<EpubAnnotation>, onJump: (Locator) -> Unit, onEdit: (String) -> Unit) {
    if (notes.isEmpty()) return EmptyHint(R.string.epub_empty_annotations)
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(notes) { note ->
            val locator = decodeLocator(note.locatorJson)
            val label = note.noteText ?: locator?.text?.highlight.orEmpty()
            MarkCard(
                onClick = locator?.let { { onJump(it) } },
                leading = {
                    Surface(
                        Modifier.padding(start = 16.dp).size(12.dp),
                        shape = CircleShape,
                        color = note.color?.argb() ?: MaterialTheme.colorScheme.primary,
                    ) {}
                },
                action = {
                    IconButton(onClick = { onEdit(note.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.reader_annotation_edit))
                    }
                },
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                MarkSecondary("${chapterOf(locator)} · ${percentOf(locator)}%")
            }
        }
    }
}

/** Shared card: [leading] marker, [body] text column, [action] button (48dp) on the right. */
@Composable
private fun MarkCard(
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit,
    action: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp), content = body)
            action()
        }
    }
}

@Composable
private fun MarkSecondary(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun chapterOf(locator: Locator?): String =
    locator?.title?.takeIf { it.isNotBlank() } ?: locator?.href?.toString().orEmpty()

private fun percentOf(locator: Locator?): Int =
    ((locator?.locations?.totalProgression ?: locator?.locations?.progression ?: 0.0) * CONTENTS_PERCENT).roundToInt()
