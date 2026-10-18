package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Reader route: EPUB -> Readium navigator; everything else -> the existing PDF reader (unchanged). */
@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel(),
    comfortViewModel: ReaderComfortViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comfort by comfortViewModel.comfort.collectAsStateWithLifecycle()
    ReadingTimeEffect()
    ReaderComfortEffect(comfort)
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            !state.isEpub -> ReaderScreen(onBack = onBack)
            else -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                EpubReaderScreen(state, viewModel, onBack)
            }
        }
        BlueLightOverlay(comfort.blueLight)
        OrientationToggle(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun EpubReaderScreen(
    state: EpubUiState,
    viewModel: EpubReaderViewModel,
    onBack: () -> Unit,
    settingsViewModel: EpubSettingsViewModel = hiltViewModel(),
    pace: ReadingPaceViewModel = hiltViewModel(),
) {
    val typography by settingsViewModel.typography.collectAsStateWithLifecycle()
    val theme by settingsViewModel.theme.collectAsStateWithLifecycle()
    val bookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val editor by viewModel.annotations.editor.collectAsStateWithLifecycle()
    val minutesLeft = rememberMinutesLeft(position, pace)
    val pinch = rememberEpubPinch(settingsViewModel)
    val selection = remember { EpubSelectionTracker() }
    val prefs = typography.copy(fontScale = pinch.live ?: typography.fontScale).toReadium(theme, isSystemInDarkTheme())
    val toc = remember(state.publication) { state.publication?.let { flattenToc(it.tableOfContents) }.orEmpty() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    val leave = rememberEpubQuickExit(viewModel, onBack)
    FlushOnPause(viewModel::flushProgress)
    Box(Modifier.fillMaxSize()) {
        if (state.publication == null) {
            Text(stringResource(R.string.reader_epub_failed))
        } else {
            // Fixed system-bar padding: toggling the overlay bars never resizes (re-flows) the book.
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val hooks = EpubHostHooks(viewModel.annotations, selection, pinch::onPinch)
                EpubNavigatorHost(state.publication, prefs, state.initialLocator, viewModel::onLocator, hooks)
                EpubSelectionPopup(selection, viewModel.annotations)
                EpubAnnotationPopup(viewModel.annotations)
            }
            PopupDismissEffects(viewModel.annotations, position) { barsVisible = !barsVisible }
            PinchBadge(pinch, Modifier.align(Alignment.Center))
        }
        val actions = EpubBarActions(
            onBack = leave,
            onContents = { showContents = true },
            onBookmark = viewModel::toggleBookmark,
            onSearch = { showSearch = true },
            onAnnotate = viewModel.annotations::toggleMode,
            onSettings = { showSettings = true },
        )
        AnimatedVisibility(barsVisible, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            EpubTopOverlay(EpubBarState(state.searchable, bookmarked), actions, viewModel.annotations) {
                if (viewModel.isQuick) EpubQuickMenu(viewModel)
            }
        }
        AnimatedVisibility(barsVisible, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            EpubBottomBar(position, minutesLeft)
        }
        EpubContentsPage(showContents, toc, viewModel) { showContents = false }
        EpubSettingsPage(showSettings, settingsViewModel) { showSettings = false }
    }
    EpubAnnotationHost(editor, viewModel.annotations)
    if (showSearch) EpubSearchEntry(viewModel) { showSearch = false }
}

/** A tap elsewhere or a page move closes the mark popup; a tap that only closed it does not also hide the bars. */
@Composable
private fun PopupDismissEffects(annotations: EpubAnnotationController, page: Any?, onToggleBars: () -> Unit) {
    EpubTapEffect { if (!annotations.dismissTap()) onToggleBars() }
    LaunchedEffect(page) { annotations.dismissTap() }
}

/** What the navigator host reports to / gets from the screen (kept as one object: parameter limit). */
internal class EpubHostHooks(
    val annotations: EpubAnnotationController,
    val selection: EpubSelectionTracker,
    val onPinch: (Float, Boolean) -> Unit,
)

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
    hooks: EpubHostHooks,
) {
    val activity = LocalContext.current as FragmentActivity
    val configuration = rememberEpubConfiguration(hooks.selection)
    // Set synchronously (before the view is created) so the fragment factory is ready when the container attaches.
    remember(publication) {
        EpubFragmentHost.delegate = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = prefs,
            configuration = configuration,
        )
    }
    EpubDecorationEffect(hooks.annotations)
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
        factory = { context -> PinchLayout(context).apply { addView(navigatorContainer(context, activity)) } },
        update = { it.onPinch = hooks.onPinch },
        onRelease = {
            activity.supportFragmentManager.findFragmentById(R.id.epub_fragment_container)?.let {
                activity.supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        },
    )
}

/** Fixed id + replace(): always a fresh fragment built by EpubFragmentHost's delegate. */
@OptIn(ExperimentalReadiumApi::class)
private fun navigatorContainer(context: Context, activity: FragmentActivity) =
    FragmentContainerView(context).apply {
        id = R.id.epub_fragment_container
        post {
            EpubFragmentHost.delegate?.let {
                activity.supportFragmentManager.beginTransaction()
                    .replace(id, EpubNavigatorFragment::class.java, null)
                    .commitNowAllowingStateLoss()
            }
        }
    }

/** Text selection only tells the Compose popup that a selection exists (the system menu stays empty). */
@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun rememberEpubConfiguration(selection: EpubSelectionTracker): EpubNavigatorFragment.Configuration =
    remember {
        EpubNavigatorFragment.Configuration(
            decorationTemplates = epubDecorationTemplates(),
            selectionActionModeCallback = EpubSelectionMenu(selection::set),
            shouldApplyInsetsPadding = false,
        ).withBundledFonts()
    }

internal fun copyText(activity: FragmentActivity, text: String?) {
    if (text.isNullOrEmpty()) return
    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}
