package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

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
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val editor by viewModel.annotations.editor.collectAsStateWithLifecycle()
    val prefs = typography.toReadium(theme, isSystemInDarkTheme())
    val toc = remember(state.publication) { state.publication?.let { flattenToc(it.tableOfContents) }.orEmpty() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    FlushOnPause(viewModel::flushProgress)
    Box(Modifier.fillMaxSize()) {
        if (state.publication == null) {
            Text(stringResource(R.string.reader_epub_failed))
        } else {
            // Fixed system-bar padding: toggling the overlay bars never resizes (re-flows) the book.
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                EpubNavigatorHost(
                    state.publication,
                    prefs,
                    state.initialLocator,
                    viewModel::onLocator,
                    viewModel.annotations,
                )
            }
            EpubTapEffect { barsVisible = !barsVisible }
        }
        val actions = EpubBarActions(
            onBack = onBack,
            onContents = { showContents = true },
            onBookmark = viewModel::toggleBookmark,
            onSearch = { showSearch = true },
            onAnnotate = viewModel.annotations::toggleMode,
            onSettings = { showSettings = true },
        )
        AnimatedVisibility(barsVisible, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            EpubTopOverlay(EpubBarState(state.searchable, bookmarked), actions, viewModel.annotations)
        }
        AnimatedVisibility(barsVisible, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            EpubBottomBar(position)
        }
    }
    EpubAnnotationHost(editor, viewModel.annotations)
    if (showContents) {
        EpubContentsSheet(toc, bookmarks, viewModel.annotations, viewModel::removeBookmark) { showContents = false }
    }
    if (showSearch) EpubSearchEntry(viewModel) { showSearch = false }
    if (showSettings) {
        EpubSettingsSheet(
            typography = typography,
            theme = theme,
            onTypography = settingsViewModel::updateTypography,
            onTheme = settingsViewModel::setTheme,
            onDismiss = { showSettings = false },
        )
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
    annotations: EpubAnnotationController,
) {
    val activity = LocalContext.current as FragmentActivity
    val configuration = rememberEpubConfiguration(annotations)
    // Set synchronously (before the view is created) so the fragment factory is ready when the container attaches.
    remember(publication) {
        EpubFragmentHost.delegate = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = prefs,
            configuration = configuration,
        )
    }
    EpubDecorationEffect(annotations)
    // Position updates -> ViewModel (debounced save + bookmark state).
    LaunchedEffect(publication) {
        EpubFragmentHost.awaitNavigator(activity).currentLocator.collect { onLocator(it) }
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

/** Selection menu (Annotate / Copy) wired to the live navigator's current selection. */
@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun rememberEpubConfiguration(annotations: EpubAnnotationController): EpubNavigatorFragment.Configuration {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    return remember {
        val menu = EpubSelectionMenu(
            annotateLabel = activity.getString(R.string.epub_annotate),
            copyLabel = activity.getString(R.string.epub_copy),
            onAnnotate = {
                scope.launch { withSelection(activity) { annotations.onSelection(encodeLocator(it.locator)) } }
            },
            onCopy = { scope.launch { withSelection(activity) { copyText(activity, it.locator.text.highlight) } } },
        )
        EpubNavigatorFragment.Configuration(selectionActionModeCallback = menu, shouldApplyInsetsPadding = false)
    }
}

private suspend fun withSelection(activity: FragmentActivity, block: (Selection) -> Unit) {
    val navigator = EpubFragmentHost.navigator(activity) ?: return
    val selection = navigator.currentSelection() ?: return
    block(selection)
    navigator.clearSelection()
}

private fun copyText(activity: FragmentActivity, text: String?) {
    if (text.isNullOrEmpty()) return
    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}
