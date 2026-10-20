package com.paperly.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.core.ui.icons.DownloadGlyph
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.feature.common.EmptyState

/** What a Library tab can bring in: a PDF, an EPUB, or a Word file that is converted to EPUB. */
internal enum class ImportKind(val mimeTypes: Array<String>) {
    PDF(arrayOf("application/pdf")),
    EPUB(arrayOf("application/epub+zip")),
    WORD(arrayOf(DOCX_MIME)),
}

/** One system file picker for the tab; a Word file goes to the converter, anything else to the normal import. */
@Composable
internal fun rememberImportStarter(viewModel: LibraryViewModel): (ImportKind) -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri == null -> Unit
            isDocx(context, uri) -> enqueueDocxConversion(context, uri) // Word file -> background EPUB conversion
            else -> viewModel.importDocument(uri.toString())
        }
    }
    return { kind -> picker.launch(kind.mimeTypes) }
}

@StringRes
internal fun LibraryType.emptyRes(): Int = when (this) {
    LibraryType.SCAN -> R.string.library_empty_scan
    LibraryType.PDF -> R.string.library_empty_pdf
    LibraryType.EPUB -> R.string.library_empty_epub
}

/**
 * Header action. Scanned: none (nothing is imported there). PDF: one import icon. EPUB: the same icon opens a
 * two-item menu, "Import EPUB" and "Doc to EPUB".
 */
@Composable
internal fun ImportAction(type: LibraryType, enabled: Boolean, onImport: (ImportKind) -> Unit) {
    when (type) {
        LibraryType.SCAN -> Unit
        LibraryType.PDF -> ImportIcon(enabled) { onImport(ImportKind.PDF) }
        LibraryType.EPUB -> {
            var open by remember { mutableStateOf(false) }
            Box {
                ImportIcon(enabled) { open = true }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    val pick = { kind: ImportKind ->
                        open = false
                        onImport(kind)
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_epub)) },
                        onClick = { pick(ImportKind.EPUB) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_doc_to_epub)) },
                        onClick = { pick(ImportKind.WORD) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportIcon(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            DownloadGlyph,
            contentDescription = stringResource(R.string.library_import),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Empty tab: the message and the way in (Scan / Import PDF / the two EPUB buttons side by side). */
@Composable
internal fun EmptyWithActions(
    message: String,
    type: LibraryType,
    onImport: (ImportKind) -> Unit,
    onScan: () -> Unit,
) {
    when (type) {
        LibraryType.SCAN -> EmptyState(message, stringResource(R.string.scan_cta), onScan)
        LibraryType.PDF -> EmptyState(message, stringResource(R.string.import_pdf)) { onImport(ImportKind.PDF) }
        LibraryType.EPUB -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onImport(ImportKind.EPUB) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_epub), maxLines = 1)
                    }
                    FilledTonalButton(onClick = { onImport(ImportKind.WORD) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_doc_to_epub), maxLines = 1)
                    }
                }
            }
        }
    }
}
