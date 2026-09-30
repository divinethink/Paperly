package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.paperly.app.feature.common.documentMeta

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val RENDER_WIDTH_FACTOR = 1.5f
private const val MAX_RENDER_WIDTH_PX = 1600

/** Full-screen Reader (not in bottom-nav). P2-B: scrolling PDF pages, pinch-zoom, fit modes; EPUB P3. */
@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fitHeight by rememberSaveable { mutableStateOf(false) }
    val bookmarks by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Spacer(Modifier.weight(1f))
            if (state.pageCount > 0) {
                val page = listState.firstVisibleItemIndex
                val marked = page in bookmarks
                TextButton(onClick = { viewModel.toggleBookmark(page) }) {
                    Text(stringResource(if (marked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add))
                }
                TextButton(onClick = { fitHeight = !fitHeight }) {
                    Text(stringResource(if (fitHeight) R.string.reader_fit_width else R.string.reader_fit_height))
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val doc = state.document
            when {
                state.loading -> CircularProgressIndicator()
                doc == null -> Text(stringResource(R.string.reader_missing))
                state.pageCount > 0 -> PdfPages(viewModel, listState, state.pageCount, state.pageAspect, fitHeight)
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
    fitHeight: Boolean,
) {
    var zoom by rememberSaveable { mutableStateOf(MIN_ZOOM) }
    BoxWithConstraints(Modifier.fillMaxSize().pinchZoom { zoom = (zoom * it).coerceIn(MIN_ZOOM, MAX_ZOOM) }) {
        val base = if (fitHeight) minOf(maxWidth, maxHeight * aspect) else maxWidth
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
                    PageItem(viewModel, index, renderPx, aspect, pageCount)
                }
            }
        }
    }
}

@Composable
private fun PageItem(viewModel: ReaderViewModel, index: Int, renderPx: Int, aspect: Float, pageCount: Int) {
    var failed by remember(index) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, index, renderPx) {
        value = viewModel.pageBitmap(index, renderPx)
        failed = value == null
    }
    val current = bitmap
    val ratio = if (current != null) current.width.toFloat() / current.height else aspect
    Box(Modifier.fillMaxWidth().aspectRatio(ratio), contentAlignment = Alignment.Center) {
        when {
            current != null -> Image(
                bitmap = remember(current) { current.asImageBitmap() },
                contentDescription = stringResource(R.string.reader_page_description, index + 1, pageCount),
                contentScale = ContentScale.FillWidth,
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
