package com.paperly.app.feature.reader

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.paperly.app.R

private const val MAX_DIGITS = 6

/** The 1-based page typed in [text] when it is a whole number inside 1..[pageCount]; null otherwise. */
internal fun parsePageNumber(text: String, pageCount: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }

/** Asks for a page number (1..[pageCount]); [onGo] gets the 1-based page. */
@Composable
internal fun GoToPageDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val target = parsePageNumber(text, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                modifier = Modifier.focusRequester(focus),
                label = { Text(stringResource(R.string.reader_go_to_page_range, pageCount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { target?.let(onGo) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onGo) }, enabled = target != null) {
                Text(stringResource(R.string.reader_go_to_page_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
