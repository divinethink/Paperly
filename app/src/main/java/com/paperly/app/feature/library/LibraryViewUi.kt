package com.paperly.app.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.icons.SortGlyph
import com.paperly.app.domain.document.LibrarySort
import com.paperly.app.domain.document.LibraryView
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

private const val PERCENT = 100
private const val LabelTracking = 0.06f
private val SortButtonSize = 48.dp
private val ContinueCoverWidth = 44.dp
private val ContinueCoverHeight = 60.dp
private val ProgressHeight = 6.dp

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.DEFAULT -> R.string.sort_default
    LibrarySort.NAME -> R.string.sort_name
    LibrarySort.ADDED -> R.string.sort_added
    LibrarySort.OPENED -> R.string.sort_opened
    LibrarySort.SIZE -> R.string.sort_size
}

private fun LibraryView.arrow(sort: LibrarySort): String = when {
    sort != this.sort || sort == LibrarySort.DEFAULT -> ""
    ascending -> " \u2191"
    else -> " \u2193"
}

/** Sort icon button with a dropdown (re-picking the active sort flips direction); the choice persists. */
@Composable
internal fun SortButton(view: LibraryView, controls: LibraryViewViewModel = hiltViewModel()) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(SortButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    SortGlyph,
                    contentDescription = stringResource(R.string.sort_label) + ": " +
                        stringResource(view.sort.labelRes()) + view.arrow(view.sort),
                )
            }
        }
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
}

/** Card for the most recently opened document with its saved progress; hidden while searching. */
@Composable
internal fun ContinueSection(
    source: StateFlow<ContinueInfo?>,
    query: String,
    onOpen: (String) -> Unit,
    covers: CoverViewModel = hiltViewModel(),
) {
    val info by source.collectAsStateWithLifecycle()
    val current = info
    if (current != null && query.isBlank()) {
        Surface(
            onClick = { onOpen(current.document.id) },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverImage(
                    current.document,
                    covers,
                    Modifier.size(ContinueCoverWidth, ContinueCoverHeight),
                    cornerRadius = 10.dp,
                    letterStyle = MaterialTheme.typography.titleLarge,
                )
                ContinueDetails(current)
            }
        }
    }
}

@Composable
private fun ContinueDetails(current: ContinueInfo) {
    Column(Modifier.padding(top = 2.dp)) {
        Text(
            stringResource(R.string.continue_reading_label).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = LabelTracking.em),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            current.document.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        current.progress?.let { progress ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(ProgressHeight).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.outline,
                    drawStopIndicator = {},
                )
                Text(
                    stringResource(R.string.continue_reading_pct, (progress * PERCENT).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
