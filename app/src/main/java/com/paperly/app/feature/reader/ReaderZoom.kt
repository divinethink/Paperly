package com.paperly.app.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 4f

/** Keeps a page-list translation inside the viewport; centred when the content is narrower than the viewport. */
internal fun clampOffset(offset: Float, viewportPx: Float, contentPx: Float): Float =
    if (contentPx <= viewportPx) (viewportPx - contentPx) / 2f else offset.coerceIn(viewportPx - contentPx, 0f)

/**
 * Zoom + horizontal translation of the page list, drawn with a top-left-origin graphicsLayer. Vertical position stays
 * in the LazyColumn scroll, so [transform] returns the list-scroll delta instead of a vertical translation.
 */
@Stable
class PageZoomState(initialZoom: Float = MIN_ZOOM) {
    var zoom by mutableFloatStateOf(initialZoom)
        private set
    private var tx by mutableFloatStateOf(0f)

    fun offsetX(viewportPx: Float, basePx: Float): Float = clampOffset(tx, viewportPx, basePx * zoom)

    /** Horizontal one-finger pan by [dx] screen px. */
    fun panX(dx: Float, viewportPx: Float, basePx: Float) {
        tx = clampOffset(offsetX(viewportPx, basePx) + dx, viewportPx, basePx * zoom)
    }

    /**
     * Pinch by [factor] about [focus] (viewport px) plus a [pan]. Keeps the content under [focus] in place and returns
     * the vertical list-scroll delta (list px) that does the same on the vertical axis.
     */
    fun transform(factor: Float, focus: Offset, pan: Offset, viewportPx: Float, basePx: Float): Float {
        val old = zoom
        val next = (old * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val startX = offsetX(viewportPx, basePx)
        tx = clampOffset(focus.x - (focus.x - startX) * (next / old) + pan.x, viewportPx, basePx * next)
        zoom = next
        return focus.y * (1f / old - 1f / next) - pan.y / next
    }

    companion object {
        val Saver: Saver<PageZoomState, Float> = Saver(save = { it.zoom }, restore = { PageZoomState(it) })
    }
}

/**
 * Two fingers: [onTransform] (zoom factor, centroid, pan), consumed. One finger: [onPan] with the horizontal drag,
 * NOT consumed, so the list's own vertical scroll still works. [panEnabled] false (Annotate mode) turns it off.
 */
@Composable
internal fun Modifier.pinchZoom(
    panEnabled: Boolean,
    onPan: (Float) -> Unit,
    onTransform: (Float, Offset, Offset) -> Unit,
): Modifier {
    val pan by rememberUpdatedState(onPan)
    val transform by rememberUpdatedState(onTransform)
    val enabled by rememberUpdatedState(panEnabled)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) {
                    transform(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                    event.changes.forEach { it.consume() }
                } else if (enabled) {
                    pan(event.calculatePan().x)
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
