package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

private val MenuTopPadding = 8.dp
private val MenuElevation = 6.dp

/**
 * Annotate | Copy for the selected PDF text (P3-K step B). Floats at the top of the page area (an overlay, so the
 * pages never shift under the finger); hidden in annotate mode and Reflow, where text selection is off.
 */
@Composable
internal fun BoxScope.PdfSelectionMenu(viewModel: ReaderViewModel, onEditor: (AnnotationEditor) -> Unit) {
    val selection by viewModel.textSelect.state.collectAsStateWithLifecycle()
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val picked = selection
    if (picked == null || annotate || reflow) return
    val context = LocalContext.current
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = MenuTopPadding),
        shape = MaterialTheme.shapes.large,
        tonalElevation = MenuElevation,
        shadowElevation = MenuElevation,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    viewModel.annotations.annotateSelection(picked.page, picked.rects)?.let(onEditor)
                    viewModel.textSelect.clear()
                },
            ) { Text(stringResource(R.string.reader_select_annotate)) }
            TextButton(
                onClick = {
                    copyText(context, picked.text)
                    viewModel.textSelect.clear()
                },
            ) { Text(stringResource(R.string.reader_select_copy)) }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}
