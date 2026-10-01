package com.paperly.app.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R

/** Hooks the search dialog to the ViewModel and the live navigator (tap a hit -> jump there). */
@Composable
internal fun EpubSearchEntry(viewModel: EpubReaderViewModel, onDismiss: () -> Unit) {
    val state by viewModel.searchState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    EpubSearchDialog(
        state = state,
        onSearch = viewModel::search,
        onSelect = {
            EpubFragmentHost.navigator(activity)?.go(it.locator, animated = false)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun EpubSearchDialog(
    state: EpubSearchState,
    onSearch: (String) -> Unit,
    onSelect: (EpubSearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.epub_search)) },
        confirmButton = { TextButton(onClick = { onSearch(query) }) { Text(stringResource(R.string.epub_search_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.epub_close)) } },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.epub_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                )
                SearchResults(state, onSelect)
            }
        },
    )
}

@Composable
private fun SearchResults(state: EpubSearchState, onSelect: (EpubSearchHit) -> Unit) {
    when {
        state.searching -> Text(stringResource(R.string.epub_searching))
        state.searched && state.hits.isEmpty() -> Text(stringResource(R.string.epub_search_empty))
        else -> LazyColumn {
            items(state.hits) { hit ->
                Text(
                    text = buildAnnotatedString {
                        append(hit.before)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(hit.match) }
                        append(hit.after)
                    },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSelect(hit) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
