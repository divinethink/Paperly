package com.paperly.app.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val BAND = 0.15f // top/bottom share of the viewport where a selection drag scrolls the page
private const val MAX_STEP_DP = 10f // scroll per frame at the very edge; slower further in

/** -1..1: how fast to scroll for a finger at [y] in a viewport [height] tall (negative = up, 0 = not near an edge). */
internal fun edgeSpeed(y: Float, height: Float): Float {
    if (height <= 0f) return 0f
    val band = height * BAND
    return when {
        y < band -> -((band - y) / band).coerceAtMost(1f)
        y > height - band -> ((y - (height - band)) / band).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * Where the finger of a running selection drag is ([y], measured from the viewport top, so it does not depend on
 * which page the drag started on), so the page list can scroll while the finger waits near the top or bottom edge.
 * [onShift] lets the drag re-pick its end point after every scroll step, because the page moves under a finger that
 * does not.
 */
@Stable
internal class EdgeScroll {
    var active by mutableStateOf(false)
        private set
    var y = 0f
        private set
    var onShift: ((Float) -> Unit)? = null

    /** [y] = finger height in viewport layout px; null = the finger is up. */
    fun track(y: Float?) {
        if (y == null) {
            active = false
        } else {
            this.y = y
            active = true
        }
    }

    /** The list scrolled by [dy]: the finger stayed, the pages moved under it. */
    fun shifted(dy: Float) {
        onShift?.invoke(dy)
    }
}

internal val LocalEdgeScroll = staticCompositionLocalOf { EdgeScroll() }

/** Scrolls [listState] a little every frame while [edge] says a selection drag sits near the top or bottom edge. */
@Composable
internal fun EdgeScrollEffect(edge: EdgeScroll, listState: LazyListState, zoomOf: () -> Float, heightPx: Float) {
    val step = with(LocalDensity.current) { MAX_STEP_DP.dp.toPx() }
    LaunchedEffect(edge.active) {
        while (edge.active) {
            withFrameNanos { }
            val zoom = zoomOf()
            val speed = edgeSpeed(edge.y * zoom, heightPx)
            if (speed != 0f) {
                val moved = listState.dispatchRawDelta(speed * step / zoom)
                if (moved != 0f) edge.shifted(moved)
            }
        }
    }
}
