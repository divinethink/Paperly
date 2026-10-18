package com.paperly.app.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.paperly.app.domain.document.Document

private const val EPUB = "epub"

/** One small author line under an EPUB title (from the book's metadata); nothing is shown when there is none. */
@Composable
internal fun AuthorLine(doc: Document, covers: CoverViewModel) {
    var author by remember(doc.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.id, doc.type) { author = if (doc.type == EPUB) covers.author(doc.id, doc.type) else null }
    author?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
