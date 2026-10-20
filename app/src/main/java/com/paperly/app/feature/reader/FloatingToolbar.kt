package com.paperly.app.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private const val BAR_ID = "bar"
private const val PANEL_ID = "panel"
private const val FADE_MS = 150
private val BarMargin = 12.dp // the bar never gets closer than this to the left/right edge
private val PanelSpace = 4.dp // between the bar and the colour panel
private val PopupShape = RoundedCornerShape(16.dp)
private val PopupElevation = 6.dp

/**
 * Where the bar goes: its top-left in container px, whether it sits above the text it acts on, and the vertical
 * limits ([top], [bottom]) the bar and its panel must stay inside.
 */
internal class BarPlacement(val origin: IntOffset, val above: Boolean, val top: Int, val bottom: Int)

/**
 * One floating bar plus an optional colour panel. The bar is placed from its own (stable) size by [place]; the panel
 * is a separate card of the same width that opens on the side away from the text (above a bar that is above the text),
 * so the bar never moves when the panel opens and the marked line stays visible. Sizes are real, measured in the same
 * pass as the placement, so nothing lags a frame behind. [place] gets the bar size and the container size (px); the
 * side margin is applied here.
 */
@Composable
internal fun FloatingToolbar(
    panelOpen: Boolean,
    place: (bar: IntSize, area: IntSize) -> BarPlacement,
    panel: @Composable () -> Unit,
    bar: @Composable () -> Unit,
) {
    Layout(
        content = {
            Surface(
                modifier = Modifier.layoutId(BAR_ID),
                shape = PopupShape,
                tonalElevation = PopupElevation,
                shadowElevation = PopupElevation,
                content = bar,
            )
            AnimatedVisibility(
                visible = panelOpen,
                modifier = Modifier.layoutId(PANEL_ID),
                enter = fadeIn(tween(FADE_MS)),
                exit = fadeOut(tween(FADE_MS)),
            ) {
                Surface(
                    shape = PopupShape,
                    tonalElevation = PopupElevation,
                    shadowElevation = PopupElevation,
                    content = panel,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val margin = BarMargin.roundToPx()
        val gap = PanelSpace.roundToPx()
        val room = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val barBox = measurables.first { it.layoutId == BAR_ID }.measure(Constraints(maxWidth = room))
        val area = IntSize(constraints.maxWidth, constraints.maxHeight)
        val raw = place(IntSize(barBox.width, barBox.height), area)
        val x = clampBarX(raw.origin.x.toFloat(), barBox.width, area.width, margin)
        val at = BarPlacement(IntOffset(x, raw.origin.y), raw.above, raw.top, raw.bottom)
        // the panel is exactly as wide as the bar
        val panelBox = measurables.firstOrNull { it.layoutId == PANEL_ID }
            ?.measure(Constraints.fixedWidth(barBox.width))
        layout(area.width, area.height) {
            barBox.place(at.origin)
            if (panelBox != null && panelBox.height > 0) {
                val y = panelY(at, barBox.height, panelBox.height, gap)
                panelBox.place(at.origin.x, y)
            }
        }
    }
}

/** Left edge of a bar of width [bar] wanted at [x]: at least [margin] from both sides of a [viewport]-wide area. */
internal fun clampBarX(x: Float, bar: Int, viewport: Int, margin: Int): Int =
    x.toInt().coerceIn(margin, (viewport - bar - margin).coerceAtLeast(margin))

/**
 * Top of the colour panel: on the side away from the text (above a bar that is above the text, below one that is
 * below it); on the other side only when the away side has no room; always inside [top]..[bottom].
 */
internal fun panelY(bar: BarPlacement, barH: Int, panelH: Int, gap: Int): Int {
    val up = bar.origin.y - gap - panelH
    val down = bar.origin.y + barH + gap
    val fitsUp = up >= bar.top
    val fitsDown = down + panelH <= bar.bottom
    val y = when {
        bar.above && (fitsUp || !fitsDown) -> up
        !bar.above && (fitsDown || !fitsUp) -> down
        bar.above -> down
        else -> up
    }
    return y.coerceIn(bar.top, (bar.bottom - panelH).coerceAtLeast(bar.top))
}
