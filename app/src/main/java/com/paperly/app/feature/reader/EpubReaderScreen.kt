package com.paperly.app.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import kotlinx.coroutines.delay
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

private const val NAVIGATOR_POLL_MS = 100L

/** Reader route: EPUB -> Readium navigator; everything else -> the existing PDF reader (unchanged). */
@Composable
fun ReaderRoute(onBack: () -> Unit, viewModel: EpubReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> Unit
        !state.isEpub -> ReaderScreen(onBack = onBack)
        else -> EpubReaderScreen(state, viewModel, onBack)
    }
}

@Composable
private fun EpubReaderScreen(
    state: EpubUiState,
    viewModel: EpubReaderViewModel,
    onBack: () -> Unit,
    settingsViewModel: EpubSettingsViewModel = hiltViewModel(),
) {
    val typography by settingsViewModel.typography.collectAsStateWithLifecycle()
    val theme by settingsViewModel.theme.collectAsStateWithLifecycle()
    val bookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val prefs = typography.toReadium(theme, isSystemInDarkTheme())
    val toc = remember(state.publication) { state.publication?.let { flattenToc(it.tableOfContents) }.orEmpty() }
    var showToc by rememberSaveable { mutableStateOf(false) }
    FlushOnPause(viewModel::flushProgress)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val actions = EpubBarActions(onBack, { showToc = true }, viewModel::toggleBookmark) { showSettings = true }
        EpubTopBar(state.title, EpubBarState(toc.isNotEmpty(), bookmarked), actions)
        if (state.publication == null) {
            Text(stringResource(R.string.reader_epub_failed))
        } else {
            EpubNavigatorHost(state.publication, prefs, state.initialLocator, viewModel::onLocator)
        }
    }
    if (showToc) {
        val activity = LocalContext.current as FragmentActivity
        EpubTocDialog(
            entries = toc,
            onSelect = {
                EpubFragmentHost.navigator(activity)?.go(it.link, animated = false)
                showToc = false
            },
            onDismiss = { showToc = false },
        )
    }
    if (showSettings) {
        EpubSettingsDialog(
            typography = typography,
            theme = theme,
            onTypography = settingsViewModel::updateTypography,
            onTheme = settingsViewModel::setTheme,
            onDismiss = { showSettings = false },
        )
    }
}

internal data class EpubBarState(val hasToc: Boolean, val bookmarked: Boolean)

internal class EpubBarActions(
    val onBack: () -> Unit,
    val onToc: () -> Unit,
    val onBookmark: () -> Unit,
    val onSettings: () -> Unit,
)

@Composable
private fun EpubTopBar(title: String, bar: EpubBarState, actions: EpubBarActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
        }
        Text(title, Modifier.weight(1f), maxLines = 1)
        // Capability-aware: no outline in the book -> no button (not a disabled one).
        if (bar.hasToc) {
            IconButton(onClick = actions.onToc) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.epub_toc))
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

/** Saves reading position when the app leaves the foreground (activity lifecycle: no Compose-local needed). */
@Composable
private fun FlushOnPause(onPause: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) onPause() }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubNavigatorHost(
    publication: Publication,
    prefs: EpubPreferences,
    initialLocator: Locator?,
    onLocator: (Locator) -> Unit,
) {
    val activity = LocalContext.current as FragmentActivity
    // Set synchronously (before the view is created) so the fragment factory is ready when the container attaches.
    remember(publication) {
        EpubFragmentHost.delegate = EpubNavigatorFactory(publication)
            .createFragmentFactory(initialLocator = initialLocator, initialPreferences = prefs)
    }
    // Position updates -> ViewModel (debounced save + bookmark state). The fragment appears right after first layout.
    LaunchedEffect(publication) {
        var navigator = EpubFragmentHost.navigator(activity)
        while (navigator == null) {
            delay(NAVIGATOR_POLL_MS)
            navigator = EpubFragmentHost.navigator(activity)
        }
        navigator.currentLocator.collect { onLocator(it) }
    }
    // Later changes (settings dialog, theme) are pushed to the live navigator.
    LaunchedEffect(prefs) {
        EpubFragmentHost.navigator(activity)?.submitPreferences(prefs)
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = R.id.epub_fragment_container
                // Fixed id + replace(): always a fresh fragment built by EpubFragmentHost's delegate.
                post {
                    EpubFragmentHost.delegate?.let {
                        activity.supportFragmentManager.beginTransaction()
                            .replace(id, EpubNavigatorFragment::class.java, null)
                            .commitNowAllowingStateLoss()
                    }
                }
            }
        },
        onRelease = { view ->
            activity.supportFragmentManager.findFragmentById(view.id)?.let {
                activity.supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        },
    )
}
