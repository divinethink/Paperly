package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HANDLE_HIDE_MS = 1500L
private const val HANDLE_ALPHA = 0.75f
private val HandleHeight = 56.dp
private val HandleTouchWidth = 32.dp

/** Fast-scroll handle on the right edge: shows while scrolling, drag it to jump through the pages, then fades away. */
@Composable
internal fun PdfScrollHandle(listState: LazyListState, pageCount: Int, enabled: Boolean) {
    var dragging by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            active = true
        } else {
            delay(HANDLE_HIDE_MS)
            active = false
        }
    }
    if (!enabled || pageCount < 2 || !(active || dragging)) return
    val edge = PaddingValues(
        top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT,
        bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding(),
    )
    val density = LocalDensity.current
    val last = pageCount - 1
    BoxWithConstraints(Modifier.fillMaxSize().padding(edge)) {
        val trackPx = (with(density) { (maxHeight - HandleHeight).toPx() }).coerceAtLeast(1f)
        val shown = if (dragging) dragFraction else (listState.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (shown * trackPx).roundToInt()) }
                .width(HandleTouchWidth)
                .height(HandleHeight)
                .pointerInput(pageCount, trackPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = (listState.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        dragFraction = (dragFraction + drag / trackPx).coerceIn(0f, 1f)
                        val target = (dragFraction * last).roundToInt()
                        if (target != listState.firstVisibleItemIndex) scope.launch { listState.scrollToItem(target) }
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                Modifier.padding(end = 4.dp).width(if (dragging) 12.dp else 8.dp).height(HandleHeight),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = HANDLE_ALPHA),
            ) {}
        }
    }
}
