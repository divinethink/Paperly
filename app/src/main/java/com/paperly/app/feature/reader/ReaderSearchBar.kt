package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.paperly.app.R

@Composable
internal fun SearchBar(controller: ReaderSearchController, state: SearchUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = controller::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.reader_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { controller.submit() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(searchStatus(state), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = controller::previous, enabled = state.matches.isNotEmpty()) {
                Text(stringResource(R.string.reader_search_prev))
            }
            TextButton(onClick = controller::next, enabled = state.matches.isNotEmpty()) {
                Text(stringResource(R.string.reader_search_next))
            }
        }
        if (state.partialMatch) {
            Text(stringResource(R.string.reader_search_partial), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun searchStatus(state: SearchUiState): String = when {
    state.searching -> stringResource(R.string.reader_search_searching)
    state.failed -> stringResource(R.string.reader_search_failed)
    state.submitted && state.matches.isEmpty() -> stringResource(R.string.reader_search_none)
    state.matches.isNotEmpty() ->
        stringResource(R.string.reader_search_count, state.index + 1, state.matches.size)
    else -> ""
}
