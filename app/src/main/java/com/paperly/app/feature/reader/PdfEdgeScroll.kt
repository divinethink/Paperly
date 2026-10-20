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
private val EdgeBand = 28.dp // about two text lines: resting a finger on the first lines must not scroll
private const val MAX_STEP_DP = 10f // scroll per frame at the very edge; slower further in

/** Where a selection drag scrolls the page: the [band] just inside the visible [top] and [bottom] edges (px). */
internal class EdgeZone(val top: Float, val bottom: Float, val band: Float)

/** The visible page area (below the top bar, above the navigation bar) with a thin [EdgeBand] at each end. */
@Composable
internal fun edgeZone(heightPx: Float): EdgeZone {
    val pad = readerEdgePadding()
    return with(LocalDensity.current) {
        EdgeZone(pad.calculateTopPadding().toPx(), heightPx - pad.calculateBottomPadding().toPx(), EdgeBand.toPx())
    }
}

/**
 * -1..1: how fast to scroll for a finger at [y] in a viewport [height] tall (negative = up, 0 = not near an edge).
 * By default the band is 15% of the height at each end; [top], [bottom] and [band] narrow it to the visible area.
 */
internal fun edgeSpeed(
    y: Float,
    height: Float,
    top: Float = 0f,
    bottom: Float = height,
    band: Float = height * BAND,
): Float {
    if (height <= 0f || band <= 0f) return 0f
    return when {
        y < top + band -> -((top + band - y) / band).coerceAtMost(1f)
        y > bottom - band -> ((y - (bottom - band)) / band).coerceAtMost(1f)
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

/** Scrolls [listState] a little every frame while a selection drag sits in the [zone]'s top or bottom band. */
@Composable
internal fun EdgeScrollEffect(
    edge: EdgeScroll,
    listState: LazyListState,
    zoomOf: () -> Float,
    heightPx: Float,
    zone: EdgeZone,
) {
    val step = with(LocalDensity.current) { MAX_STEP_DP.dp.toPx() }
    LaunchedEffect(edge.active) {
        while (edge.active) {
            withFrameNanos { }
            val zoom = zoomOf()
            val speed = edgeSpeed(edge.y * zoom, heightPx, zone.top, zone.bottom, zone.band)
            if (speed != 0f) {
                val moved = listState.dispatchRawDelta(speed * step / zoom)
                if (moved != 0f) edge.shifted(moved)
            }
        }
    }
}
