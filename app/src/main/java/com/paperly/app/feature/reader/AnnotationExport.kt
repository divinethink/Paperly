package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.intent.shareText
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationExportEntry
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.buildAnnotationMarkdown

/** EPUB: chapter heading + the selected text (read from the stored Readium locator) + the note. */
internal fun EpubAnnotation.toExportEntry(): AnnotationExportEntry {
    val locator = decodeLocator(locatorJson)
    return AnnotationExportEntry(locator?.title.orEmpty(), type, locator?.text?.highlight, noteText)
}

/** PDF annotations store only a page and a rectangle (no text), so the export lists page, type and note. */
internal fun Annotation.toExportEntry(): AnnotationExportEntry =
    AnnotationExportEntry("Page ${page + 1}", type, null, noteText)

/** Share-sheet button for the EPUB Annotations tab (hidden when there is nothing to share). */
@Composable
internal fun ShareEpubAnnotationsButton(items: List<EpubAnnotation>, title: String) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    TextButton(
        onClick = { shareText(context, title, buildAnnotationMarkdown(title, items.map { it.toExportEntry() })) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.annotations_share))
    }
}

/** ⋮ menu entry for the PDF reader (hidden when there is nothing to share). */
@Composable
internal fun SharePdfAnnotationsItem(viewModel: ReaderViewModel, onDone: () -> Unit) {
    val items by viewModel.annotations.items.collectAsStateWithLifecycle()
    if (items.isEmpty()) return
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text(stringResource(R.string.annotations_share)) },
        onClick = {
            val title = viewModel.uiState.value.document?.title.orEmpty()
            shareText(context, title, buildAnnotationMarkdown(title, items.map { it.toExportEntry() }))
            onDone()
        },
    )
}
