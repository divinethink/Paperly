package com.paperly.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.paperly.app.R
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

private const val BAR_ALPHA = 0.95f
private const val PERCENT = 100

@Composable
private fun barColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = BAR_ALPHA)

internal data class EpubBarState(val canSearch: Boolean, val bookmarked: Boolean)

internal class EpubBarActions(
    val onBack: () -> Unit,
    val onContents: () -> Unit,
    val onBookmark: () -> Unit,
    val onSearch: () -> Unit,
    val onSettings: () -> Unit,
)

/** Overlay bar (toggling it never reflows the book): Back, Contents ... Search, Bookmark, Settings. */
@Composable
internal fun EpubTopBar(bar: EpubBarState, actions: EpubBarActions, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(barColor()).statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
        }
        IconButton(onClick = actions.onContents) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.epub_toc))
        }
        Spacer(Modifier.weight(1f))
        // Capability-aware: a book without a search service gets no button (not a disabled one).
        if (bar.canSearch) {
            IconButton(onClick = actions.onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.epub_search))
            }
        }
        IconButton(onClick = actions.onBookmark) {
            Icon(
                Icons.Filled.Star,
                contentDescription = stringResource(
                    if (bar.bookmarked) R.string.epub_bookmark_remove else R.string.epub_bookmark_add,
                ),
                tint = if (bar.bookmarked) MaterialTheme.colorScheme.primary else Color.Gray,
            )
        }
        IconButton(onClick = actions.onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.epub_settings))
        }
    }
}

/** Thin progress line + "chapter ... percent" (from the live locator; whole-book progress). */
@Composable
internal fun EpubBottomBar(locator: Locator?, modifier: Modifier = Modifier) {
    val fraction = (locator?.locations?.totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth().background(barColor()).navigationBarsPadding()) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                locator?.title.orEmpty(),
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            Text("${(fraction * PERCENT).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A tap on the page (not on a link/annotation/edge) toggles the bars. */
@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubTapEffect(onTap: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val latest by rememberUpdatedState(onTap)
    LaunchedEffect(Unit) {
        val navigator = EpubFragmentHost.awaitNavigator(activity)
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                latest()
                return true
            }
        }
        navigator.addInputListener(listener)
        try {
            awaitCancellation()
        } finally {
            navigator.removeInputListener(listener)
        }
    }
}
