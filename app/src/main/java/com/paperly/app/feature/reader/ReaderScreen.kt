package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.theme.ReadingFontFamily
import com.paperly.app.core.ui.theme.readerPalette
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.feature.common.documentMeta
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val RENDER_WIDTH_FACTOR = 1.5f
private const val MAX_RENDER_WIDTH_PX = 2048

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
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    LaunchedEffect(search.jump) { search.jump?.let { listState.animateScrollToItem(it.page) } }
    val palette = readerPalette(theme, isSystemInDarkTheme())
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = palette.content)) {
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.content) {
            ReaderContent(state, viewModel, listState, palette.pageFilter, onBack)
        }
    }
}

private val HighlightColor = Color(0x66FFEB3B) // translucent yellow, readable on every reader theme

private data class PageSpec(
    val aspect: Float,
    val pageCount: Int,
    val filter: ColorFilter?,
    val highlights: List<MatchRect>,
    val annotations: List<Annotation>,
    val hooks: AnnotationHooks,
)

private data class PageLook(
    val fitHeight: Boolean,
    val filter: ColorFilter?,
    val highlights: Map<Int, List<MatchRect>>,
    val hooks: AnnotationHooks,
)

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    listState: LazyListState,
    pageFilter: ColorFilter?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val annotations by viewModel.annotations.items.collectAsStateWithLifecycle()
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    var fitHeight by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<AnnotationEditor?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Spacer(Modifier.weight(1f))
            if (search.available) {
                IconButton(onClick = viewModel.search::toggle) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.reader_search))
                }
            }
            if (state.pageCount > 0) {
                ReaderActions(viewModel, listState.firstVisibleItemIndex, fitHeight) { fitHeight = !fitHeight }
            }
        }
        if (search.open) SearchBar(viewModel.search, search)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val doc = state.document
            when {
                state.loading -> CircularProgressIndicator()
                doc == null -> Text(stringResource(R.string.reader_missing))
                state.pageCount > 0 -> {
                    val highlights = if (search.open && search.submitted) search.rects else emptyMap()
                    val hooks = AnnotationHooks(
                        items = annotations.groupBy { it.page },
                        onCreate = { page, rect -> editor = AnnotationEditor(page, rect, null) },
                        onTap = { editor = AnnotationEditor(it.page, it.rect, it) },
                    )
                    val look = PageLook(fitHeight, pageFilter, highlights, hooks)
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
    AnnotationEditorHost(editor, viewModel.annotations) { editor = null }
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
    val hScroll = rememberScrollState()
    var pendingScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingScroll) {
        val target = pendingScroll ?: return@LaunchedEffect
        withFrameNanos { } // let the wider layout be measured first, or scrollTo would clamp to the old width
        hScroll.scrollTo(target)
    }
    val onPinch: (Float, Offset) -> Unit = { factor, focus ->
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = next / zoom
        if (ratio != 1f) {
            // Keep the content under the fingers in place (zoom about the pinch point, not the left edge).
            pendingScroll = ((hScroll.value + focus.x) * ratio - focus.x).roundToInt().coerceAtLeast(0)
            zoom = next
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().pinchZoom(onPinch)) {
        val base = if (look.fitHeight) minOf(maxWidth, maxHeight * aspect) else maxWidth
        val viewportWidth = maxWidth
        // Render resolution follows the zoom step (1.5x, 2x, 3x, 4x of the page width) so zoomed text stays sharp.
        val renderPx = with(LocalDensity.current) {
            (base.toPx() * maxOf(RENDER_WIDTH_FACTOR, ceil(zoom))).toInt().coerceAtMost(MAX_RENDER_WIDTH_PX)
        }
        Box(
            Modifier.fillMaxSize().horizontalScroll(hScroll).widthIn(min = viewportWidth),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(Modifier.width(base * zoom), state = listState) {
                items(count = pageCount, key = { it }) { index ->
                    val spec = PageSpec(
                        aspect = aspect,
                        pageCount = pageCount,
                        filter = look.filter,
                        highlights = look.highlights[index].orEmpty(),
                        annotations = look.hooks.items[index].orEmpty(),
                        hooks = look.hooks,
                    )
                    PageItem(viewModel, index, renderPx, spec)
                }
            }
        }
    }
}

@Composable
private fun PageItem(viewModel: ReaderViewModel, index: Int, renderPx: Int, spec: PageSpec) {
    var failed by remember(index) { mutableStateOf(false) }
    var shown by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, renderPx) {
        // The previous (lower-resolution) bitmap stays on screen until the sharper one is ready.
        val fresh = viewModel.pageBitmap(index, renderPx)
        if (fresh != null) shown = fresh
        failed = fresh == null && shown == null
    }
    val current = shown
    val ratio = if (current != null) current.width.toFloat() / current.height else spec.aspect
    var draft by remember(index) { mutableStateOf<MatchRect?>(null) }
    val gestures = Modifier.annotationGestures(
        annotations = spec.annotations,
        onDraft = { draft = it },
        onCreate = { spec.hooks.onCreate(index, it) },
        onTap = spec.hooks.onTap,
    )
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).then(gestures).drawWithContent {
            drawContent()
            spec.highlights.forEach { r ->
                drawRect(
                    color = HighlightColor,
                    topLeft = Offset(r.left * size.width, r.top * size.height),
                    size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
                )
            }
            drawAnnotations(spec.annotations)
            draft?.let { drawDraft(it) }
        },
        contentAlignment = Alignment.Center,
    ) {
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
private fun Modifier.pinchZoom(onZoom: (Float, Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) {
                onZoom(event.calculateZoom(), event.calculateCentroid())
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
private fun SearchBar(controller: ReaderSearchController, state: SearchUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = controller::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.reader_search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { controller.submit() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(searchStatus(state), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = controller::previous, enabled = state.matches.isNotEmpty()) {
                Text(stringResource(R.string.reader_search_prev))
            }
            TextButton(onClick = controller::next, enabled = state.matches.isNotEmpty()) {
                Text(stringResource(R.string.reader_search_next))
            }
        }
        if (state.partialMatch) {
            Text(stringResource(R.string.reader_search_partial), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun searchStatus(state: SearchUiState): String = when {
    state.searching -> stringResource(R.string.reader_search_searching)
    state.failed -> stringResource(R.string.reader_search_failed)
    state.submitted && state.matches.isEmpty() -> stringResource(R.string.reader_search_none)
    state.matches.isNotEmpty() ->
        stringResource(R.string.reader_search_count, state.index + 1, state.matches.size)
    else -> ""
}
