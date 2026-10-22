package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import kotlin.math.ceil

private const val MAX_PAGE_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val RENDER_WIDTH_FACTOR = 1.5f
private const val MAX_RENDER_WIDTH_PX = 2048
private val BOTTOM_BAR_ROOM = 56.dp // the scan bottom bar floats over the page area

/**
 * Scanned documents in view mode: one page per screen, swipe left / right (like Notebloc). It uses the Reader's own
 * [listState] (as a horizontal list with snapping), so page number, progress, bars and search jumps keep working.
 * Pinch / double-tap zooms inside the page; while zoomed the page pans and the swipe is paused.
 */
@Composable
internal fun ScanPages(viewModel: ReaderViewModel, listState: LazyListState, pageCount: Int, hooks: AnnotationHooks) {
    var zoomed by remember { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        userScrollEnabled = !zoomed,
        flingBehavior = rememberSnapFlingBehavior(listState),
    ) {
        items(count = pageCount, key = { it }) { index ->
            ScanPage(viewModel, index, pageCount, hooks) { zoomed = it }
        }
    }
}

@Composable
private fun LazyItemScope.ScanPage(
    viewModel: ReaderViewModel,
    index: Int,
    pageCount: Int,
    hooks: AnnotationHooks,
    onZoomed: (Boolean) -> Unit,
) {
    val zoom = remember { PageZoom() }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index) { mutableStateOf(false) }
    val edge = readerEdgePadding()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillParentMaxSize()
            .padding(top = edge.calculateTopPadding(), bottom = edge.calculateBottomPadding() + BOTTOM_BAR_ROOM)
            .clipToBounds()
            .zoomGestures(zoom, hooks.pending.onBlankTap),
        contentAlignment = Alignment.Center,
    ) {
        val box = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val wanted = box.width * maxOf(RENDER_WIDTH_FACTOR, ceil(zoom.scale))
        val renderPx = wanted.toInt().coerceAtMost(MAX_RENDER_WIDTH_PX)
        SideEffect { zoom.box = box }
        LaunchedEffect(index, renderPx) {
            val fresh = viewModel.pageBitmap(index, renderPx) // the lower-resolution page stays until this is ready
            if (fresh != null) bitmap = fresh
            failed = fresh == null && bitmap == null
        }
        LaunchedEffect(zoom.scale > 1f) { onZoomed(zoom.scale > 1f) }
        DisposableEffect(Unit) { onDispose { onZoomed(false) } }
        val page = bitmap
        when {
            page != null -> {
                val ratio = page.width.toFloat() / page.height
                val fitWidth = minOf(maxWidth, maxHeight * ratio)
                SideEffect { zoom.image = with(density) { Size(fitWidth.toPx(), (fitWidth / ratio).toPx()) } }
                val marks = hooks.items[index].orEmpty()
                Image(
                    bitmap = remember(page) { page.asImageBitmap() },
                    contentDescription = stringResource(R.string.reader_page_description, index + 1, pageCount),
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.size(fitWidth, fitWidth / ratio).graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }.drawWithContent {
                        drawContent()
                        drawAnnotations(marks, zoom.scale)
                    },
                )
            }
            failed -> Text(stringResource(R.string.reader_page_failed))
            else -> CircularProgressIndicator()
        }
    }
}

/** Zoom and pan of one scanned page (scale about the page centre; the offset keeps the page inside the area). */
@Stable
private class PageZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var box = Size.Zero
    var image = Size.Zero

    /** Pinch by [change] about [focus] (area px) plus a [pan]; keeps the content under the fingers in place. */
    fun transform(change: Float, focus: Offset, pan: Offset) {
        val next = (scale * change).coerceIn(1f, MAX_PAGE_ZOOM)
        val c = focus - Offset(box.width / 2, box.height / 2)
        offset = clamp(c - (c - offset) * (next / scale) + pan, next)
        scale = next
    }

    /** Double tap: zoom in, or back to the whole page. */
    fun toggle() {
        if (scale > 1f) reset() else transform(DOUBLE_TAP_ZOOM, Offset(box.width / 2, box.height / 2), Offset.Zero)
    }

    fun settle() {
        if (scale <= 1f) reset()
    }

    private fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(value: Offset, at: Float): Offset {
        val maxX = maxOf(0f, (image.width * at - box.width) / 2)
        val maxY = maxOf(0f, (image.height * at - box.height) / 2)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }
}

/** Tap = bars, double tap = zoom, two fingers = pinch; one finger pans only while zoomed (else the page swipes). */
private fun Modifier.zoomGestures(zoom: PageZoom, onTap: () -> Unit): Modifier =
    pointerInput(zoom) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { zoom.toggle() }) }
        .pointerInput(zoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1 || zoom.scale > 1f) {
                        zoom.transform(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                zoom.settle()
            }
        }
