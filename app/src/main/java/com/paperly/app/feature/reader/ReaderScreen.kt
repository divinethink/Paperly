package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.MatchRect
import com.paperly.app.feature.common.documentMeta
import kotlin.math.ceil

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
    val leave = rememberQuickExit(viewModel, state.document, onBack)
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = palette.content)) {
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.content) {
            ReaderContent(state, viewModel, listState, palette.pageFilter, leave)
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
    val onEditor: (AnnotationEditor) -> Unit,
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
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    val pending by viewModel.annotations.pending.collectAsStateWithLifecycle()
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
        PdfAnnotateBars(viewModel.annotations, annotate) { editor = it }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val doc = state.document
            when {
                state.loading -> CircularProgressIndicator()
                doc == null -> Text(stringResource(R.string.reader_missing))
                state.pageCount > 0 -> {
                    val highlights = if (search.open && search.submitted) search.rects else emptyMap()
                    val hooks = annotationHooks(annotations, annotate, viewModel.annotations, pending) { editor = it }
                        .withSelect(viewModel, enabled = !annotate)
                    val look = PageLook(fitHeight, pageFilter, highlights, hooks) { editor = it }
                    PdfOrReflow(viewModel, state, listState, look)
                    PdfSelectionMenu(viewModel) { editor = it }
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

/** Dragged areas become pending parts (committed together with Done); tapping a saved annotation opens its dialog. */
private fun annotationHooks(
    items: List<Annotation>,
    annotate: Boolean,
    controller: AnnotationController,
    pending: PendingParts?,
    openEditor: (AnnotationEditor) -> Unit,
) = AnnotationHooks(
    items = items.groupBy { it.page },
    annotate = annotate,
    onCreate = controller::addPending,
    onTap = { openEditor(AnnotationEditor(it.page, it.rect, it)) },
    pending = PendingHooks(pending, controller::changePending, controller::removePending),
)

/** Page images (default) or the text Reflow view (P3-G); Reflow is its own path and never alters PDF progress. */
@Composable
private fun PdfOrReflow(viewModel: ReaderViewModel, state: ReaderUiState, listState: LazyListState, look: PageLook) {
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    if (reflow) {
        ReflowPages(viewModel.search, state.pageCount, if (search.needsOcr) viewModel.reflowOcr else null)
    } else {
        PdfPages(viewModel, listState, state.pageCount, state.pageAspect, look)
        PdfPaceOverlay(listState, state.pageCount)
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
    val zoomState = rememberSaveable(saver = PageZoomState.Saver) { PageZoomState() }
    val annotate = look.hooks.annotate
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val base = if (look.fitHeight) minOf(maxWidth, maxHeight * aspect) else maxWidth
        val viewportHeight = maxHeight
        val viewportPx = with(density) { maxWidth.toPx() }
        val basePx = with(density) { base.toPx() }
        val zoom = zoomState.zoom
        // Render resolution follows the zoom step (1.5x, 2x, 3x, 4x of the page width) so zoomed text stays sharp.
        val renderPx = (basePx * maxOf(RENDER_WIDTH_FACTOR, ceil(zoom))).toInt().coerceAtMost(MAX_RENDER_WIDTH_PX)
        val gestureLock = remember { GestureLock() }
        val edgeScroll = remember { EdgeScroll() }
        val gesture = Modifier.fillMaxSize().pinchZoom(
            panEnabled = !annotate,
            lock = gestureLock,
            onPan = { zoomState.panX(it, viewportPx, basePx) },
            onTransform = { factor, focus, pan ->
                listState.dispatchRawDelta(zoomState.transform(factor, focus, pan, viewportPx, basePx))
            },
        )
        val zoomProvider = remember(zoomState) { { zoomState.zoom } }
        CompositionLocalProvider(
            LocalPageZoom provides zoomProvider,
            LocalGestureLock provides gestureLock,
            LocalEdgeScroll provides edgeScroll,
            LocalPageLocator provides remember(listState) { PageLocator(listState) },
        ) {
            Box(gesture) {
                // Layout height shrinks by the zoom so the scaled list exactly fills the viewport.
                LazyColumn(
                    modifier = Modifier.width(base).height(viewportHeight / zoom).graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = zoomState.offsetX(viewportPx, basePx)
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                    state = listState,
                    userScrollEnabled = !annotate,
                ) {
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
        val heightPx = with(density) { viewportHeight.toPx() }
        EdgeScrollEffect(edgeScroll, listState, { zoomState.zoom }, heightPx)
        val port = SelectionViewport(listState, zoomState, basePx, viewportPx, heightPx)
        PdfSelectionPopup(viewModel, port, look.onEditor)
    }
}

/** Selection wiring of page [index]; [onFinger] also moves the loupe. Null = text selection off. */
@Composable
private fun pageSelectEdit(index: Int, hooks: SelectHooks?, onFinger: (Offset?) -> Unit): SelectEdit? {
    val edgeScroll = LocalEdgeScroll.current
    val locator = LocalPageLocator.current
    val lock = LocalGestureLock.current
    return hooks?.let {
        SelectEdit(index, it, lock, edgeScroll, locator) { touch ->
            onFinger(touch)
            // near the top/bottom edge the page scrolls on (the finger may be over another page by now)
            edgeScroll.track(touch?.let { t -> locator?.viewportY(index, t.y) })
            it.onDragging(touch != null) // the mark panel hides while a finger is down
        }
    }
}

/** Highlights, marks, selection (round handles only if [handles]) and the pending mark of page [index]. */
private fun DrawScope.drawPageOverlay(
    spec: PageSpec,
    index: Int,
    pending: List<MatchRect>,
    zoom: Float,
    handles: Boolean,
) {
    spec.highlights.forEach { r ->
        drawRect(
            color = HighlightColor,
            topLeft = Offset(r.left * size.width, r.top * size.height),
            size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
        )
    }
    drawAnnotations(spec.annotations, zoom)
    spec.hooks.select?.selection?.let { sel ->
        sel.rectsOn(index).takeIf { it.isNotEmpty() }
            ?.let { drawSelection(it, zoom, handles && sel.startsOn(index), handles && sel.endsOn(index)) }
    }
    drawPending(pending, zoom)
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
    var finger by remember(index) { mutableStateOf<Offset?>(null) }
    val zoomOf = LocalPageZoom.current
    val edit = pendingEdit(index, spec.hooks, onDraft = { draft = it }, onFinger = { finger = it })
    val select = pageSelectEdit(index, spec.hooks.select) { finger = it }
    val gestures = Modifier.annotationGestures(spec.annotations, spec.hooks.annotate, edit, select, spec.hooks.onTap)
    val picture = remember(current) { current?.asImageBitmap() }
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).then(gestures).drawWithContent {
            drawContent()
            // page overlays; the loupe replays the same drawing so marks/selection show inside it too
            val overlay: DrawScope.(Boolean) -> Unit = { handles ->
                drawPageOverlay(spec, index, edit.rects, zoomOf(), handles)
            }
            overlay(true)
            draft?.let { drawDraft(it) }
            val touch = finger
            // the loupe magnifies this page only: not while the finger is over a neighbouring page
            if (touch != null && picture != null && touch.y in 0f..size.height) {
                drawLoupe(picture, touch, zoomOf(), spec.filter, overlay)
            }
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

private fun errorText(error: ReaderError): Int = when (error) {
    ReaderError.PASSWORD_REQUIRED -> R.string.reader_password_required
    ReaderError.UNSUPPORTED -> R.string.reader_unsupported
    else -> R.string.reader_open_failed
}
