@file:OptIn(ExperimentalLayoutApi::class)

package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val BADGE_ALPHA = 0.85f
private const val BADGE_HIDE_MS = 1500L

/** "5 / 15" badge: while the bars are shown it stays; with bars hidden it appears on a page change, then fades away. */
@Composable
internal fun PdfPageBadge(listState: LazyListState, pageCount: Int, barsShown: Boolean, enabled: Boolean) {
    if (!enabled || pageCount <= 0) return
    val page = listState.firstVisibleItemIndex
    var recent by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        recent = true
        delay(BADGE_HIDE_MS)
        recent = false
    }
    if (!barsShown && !recent) return
    // Below the top bar while it is shown (its height is fixed), otherwise at the very top.
    val top = if (barsShown) TOP_BAR_HEIGHT else 0.dp
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
        Surface(
            Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = top + 8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = BADGE_ALPHA),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "${page + 1} / $pageCount",
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
