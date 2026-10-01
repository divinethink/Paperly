package com.paperly.app.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
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
private fun EpubReaderScreen(state: EpubUiState, viewModel: EpubReaderViewModel, onBack: () -> Unit) {
    val typography by viewModel.typography.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val prefs = typography.toReadium(theme, isSystemInDarkTheme())
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Text(state.title, Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.epub_settings))
            }
        }
        if (state.publication == null) {
            Text(stringResource(R.string.reader_epub_failed))
        } else {
            EpubNavigatorHost(state.publication, prefs)
        }
    }
    if (showSettings) {
        EpubSettingsDialog(
            typography = typography,
            theme = theme,
            onTypography = viewModel::updateTypography,
            onTheme = viewModel::setTheme,
            onDismiss = { showSettings = false },
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubNavigatorHost(publication: Publication, prefs: EpubPreferences) {
    val activity = LocalContext.current as FragmentActivity
    // Set synchronously (before the view is created) so the fragment factory is ready when the container attaches.
    remember(publication) {
        EpubFragmentHost.delegate = EpubNavigatorFactory(publication)
            .createFragmentFactory(initialLocator = null, initialPreferences = prefs)
    }
    // Later changes (settings dialog, theme) are pushed to the live navigator.
    LaunchedEffect(prefs) {
        (activity.supportFragmentManager.findFragmentById(R.id.epub_fragment_container) as? EpubNavigatorFragment)
            ?.submitPreferences(prefs)
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
