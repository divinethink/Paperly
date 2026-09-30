package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.theme.ReadingFontFamily
import com.paperly.app.core.ui.theme.readerPalette
import com.paperly.app.domain.reader.ReaderTheme
import com.paperly.app.feature.common.documentMeta

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val RENDER_WIDTH_FACTOR = 1.5f
private const val MAX_RENDER_WIDTH_PX = 1600

/** Full-screen Reader (not in bottom-nav). P2-D: scrolling PDF pages, zoom, fit modes, themes; EPUB P3. */
@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var resumed by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flush() }
    LaunchedEffect(listState, state.pageCount) {
        if (state.pageCount > 0) {
            // Resume once per session, BEFORE tracking starts, so the initial page 0 never overwrites the saved page.
            if (!resumed) {
                if (state.startPage > 0) listState.scrollToItem(state.startPage)
                resumed = true
            }
            snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.onPageChanged(it) }
        }
    }
    val palette = readerPalette(theme, isSystemInDarkTheme())
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = palette.content)) {
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.content) {
            ReaderContent(state, viewModel, listState, palette.pageFilter, onBack)
        }
    }
}

private data class PageSpec(val aspect: Float, val pageCount: Int, val filter: ColorFilter?)

private data class PageLook(val fitHeight: Boolean, val filter: ColorFilter?)

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    listState: LazyListState,
    pageFilter: ColorFilter?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    var fitHeight by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Spacer(Modifier.weight(1f))
            if (state.pageCount > 0) {
                val page = listState.firstVisibleItemIndex
                TextButton(onClick = { viewModel.toggleBookmark(page) }) {
                    val label = if (page in bookmarks) R.string.reader_bookmark_remove else R.string.reader_bookmark_add
                    Text(stringResource(label))
                }
                TextButton(onClick = { fitHeight = !fitHeight }) {
                    Text(stringResource(if (fitHeight) R.string.reader_fit_width else R.string.reader_fit_height))
                }
                ThemeMenu(theme, viewModel::setTheme)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val doc = state.document
            when {
                state.loading -> CircularProgressIndicator()
                doc == null -> Text(stringResource(R.string.reader_missing))
                state.pageCount > 0 -> {
                    val look = PageLook(fitHeight, pageFilter)
                    PdfPages(viewModel, listState, state.pageCount, state.pageAspect, look)
                }
                else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ReadingFontFamily,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(documentMeta(context, doc), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(errorText(state.error)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PdfPages(
    viewModel: ReaderViewModel,
    listState: LazyListState,
    pageCount: Int,
    aspect: Float,
    look: PageLook,
) {
    var zoom by rememberSaveable { mutableStateOf(MIN_ZOOM) }
    BoxWithConstraints(Modifier.fillMaxSize().pinchZoom { zoom = (zoom * it).coerceIn(MIN_ZOOM, MAX_ZOOM) }) {
        val base = if (look.fitHeight) minOf(maxWidth, maxHeight * aspect) else maxWidth
        val viewportWidth = maxWidth
        val renderPx = with(LocalDensity.current) {
            (viewportWidth.toPx() * RENDER_WIDTH_FACTOR).toInt().coerceAtMost(MAX_RENDER_WIDTH_PX)
        }
        Box(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).widthIn(min = viewportWidth),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(Modifier.width(base * zoom), state = listState) {
                items(count = pageCount, key = { it }) { index ->
                    PageItem(viewModel, index, renderPx, PageSpec(aspect, pageCount, look.filter))
                }
            }
        }
    }
}

@Composable
private fun PageItem(viewModel: ReaderViewModel, index: Int, renderPx: Int, spec: PageSpec) {
    var failed by remember(index) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, index, renderPx) {
        value = viewModel.pageBitmap(index, renderPx)
        failed = value == null
    }
    val current = bitmap
    val ratio = if (current != null) current.width.toFloat() / current.height else spec.aspect
    Box(Modifier.fillMaxWidth().aspectRatio(ratio), contentAlignment = Alignment.Center) {
        when {
            current != null -> Image(
                bitmap = remember(current) { current.asImageBitmap() },
                contentDescription = stringResource(R.string.reader_page_description, index + 1, spec.pageCount),
                contentScale = ContentScale.FillWidth,
                colorFilter = spec.filter,
                modifier = Modifier.fillMaxWidth(),
            )
            failed -> Text(stringResource(R.string.reader_page_failed))
            else -> CircularProgressIndicator()
        }
    }
}

/** Two-finger pinch only: single-finger drags still reach the list's scroll. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) {
                onZoom(event.calculateZoom())
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private fun errorText(error: ReaderError): Int = when (error) {
    ReaderError.PASSWORD_REQUIRED -> R.string.reader_password_required
    ReaderError.UNSUPPORTED -> R.string.reader_unsupported
    else -> R.string.reader_open_failed
}

@Composable
private fun ThemeMenu(current: ReaderTheme, onSelect: (ReaderTheme) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.reader_theme)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        ReaderTheme.entries.forEach { option ->
            val name = stringResource(themeName(option))
            DropdownMenuItem(
                text = { Text(if (option == current) "✓ $name" else name) },
                onClick = {
                    onSelect(option)
                    open = false
                },
            )
        }
    }
}

private fun themeName(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.AUTO -> R.string.reader_theme_auto
    ReaderTheme.LIGHT -> R.string.reader_theme_light
    ReaderTheme.SEPIA -> R.string.reader_theme_sepia
    ReaderTheme.WARM -> R.string.reader_theme_warm
    ReaderTheme.DARK -> R.string.reader_theme_dark
    ReaderTheme.AMOLED -> R.string.reader_theme_amoled
}
