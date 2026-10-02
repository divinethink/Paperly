package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import org.readium.r2.shared.publication.Locator

private const val LABEL_ALPHA = 0.8f

/** "N pages left · ~M min" label for the PDF page view. Pass-through for touches (plain Box, no click handling). */
@Composable
internal fun PdfPaceOverlay(
    listState: LazyListState,
    pageCount: Int,
    pace: ReadingPaceViewModel = hiltViewModel(),
) {
    val minutes by pace.minutesLeft.collectAsStateWithLifecycle()
    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { pace.record((it + 1f) / pageCount) }
    }
    val page = listState.firstVisibleItemIndex
    val left = (pageCount - page - 1).coerceAtLeast(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Surface(
            Modifier.navigationBarsPadding().padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = LABEL_ALPHA),
            shape = MaterialTheme.shapes.small,
        ) {
            val pages = pluralStringResource(R.plurals.reader_pages_left, left, left)
            val text = minutes?.let { stringResource(R.string.reader_pages_and_minutes, pages, it) } ?: pages
            Text(
                text,
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Feeds the EPUB position into the session pace (bars may be hidden; the ViewModel keeps the data). */
@Composable
internal fun rememberMinutesLeft(position: Locator?, pace: ReadingPaceViewModel): Int? {
    val minutes by pace.minutesLeft.collectAsStateWithLifecycle()
    val progress = (position?.locations?.totalProgression ?: 0.0).toFloat()
    LaunchedEffect(progress) { if (position != null) pace.record(progress) }
    return minutes
}
