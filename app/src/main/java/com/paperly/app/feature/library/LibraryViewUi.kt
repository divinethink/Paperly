package com.paperly.app.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.document.LibraryView
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

private const val PERCENT = 100

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.DEFAULT -> R.string.sort_default
    LibrarySort.NAME -> R.string.sort_name
    LibrarySort.ADDED -> R.string.sort_added
    LibrarySort.OPENED -> R.string.sort_opened
    LibrarySort.SIZE -> R.string.sort_size
}

@StringRes
private fun LibraryType.labelRes(): Int = when (this) {
    LibraryType.PDF -> R.string.type_pdf
    LibraryType.EPUB -> R.string.type_epub
    LibraryType.SCAN -> R.string.type_scan
}

private fun LibraryView.arrow(sort: LibrarySort): String = when {
    sort != this.sort || sort == LibrarySort.DEFAULT -> ""
    ascending -> " \u2191"
    else -> " \u2193"
}

@Composable
private fun sortChipLabel(view: LibraryView): String =
    stringResource(R.string.sort_label) + ": " + stringResource(view.sort.labelRes()) + view.arrow(view.sort)

/** Sort dropdown (re-picking the active sort flips direction) + PDF/EPUB/Scan type chips; choices persist. */
@Composable
internal fun ViewRow(view: LibraryView, controls: LibraryViewViewModel = hiltViewModel()) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { menuOpen = true },
                label = { Text(sortChipLabel(view)) },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                LibrarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes()) + view.arrow(sort)) },
                        onClick = {
                            menuOpen = false
                            controls.setSort(sort)
                        },
                    )
                }
            }
        }
        LibraryType.entries.forEach { type ->
            FilterChip(
                selected = view.type == type,
                onClick = { controls.setType(type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
}

/** Card for the most recently opened document with its saved progress; hidden while searching. */
@Composable
internal fun ContinueSection(source: StateFlow<ContinueInfo?>, query: String, onOpen: (String) -> Unit) {
    val info by source.collectAsStateWithLifecycle()
    val current = info
    if (current != null && query.isBlank()) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable { onOpen(current.document.id) },
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.continue_reading_label), style = MaterialTheme.typography.labelMedium)
                Text(
                    current.document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current.progress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.continue_reading_percent, (progress * PERCENT).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
