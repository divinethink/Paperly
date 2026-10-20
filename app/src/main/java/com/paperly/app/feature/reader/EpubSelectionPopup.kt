package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.lookFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MenuGap = 36.dp // the bar sits this far above the selected text (handles hang below it)
private val MenuElevation = 6.dp
private val PanelShape = RoundedCornerShape(16.dp)
private const val POLL_MS = 150L
private const val HALF = 0.5f
private const val SETTLE_POLLS = 2 // the bar shows once the selection has not moved for this many polls
private const val SPOT_TOL_PX = 2f

/** Where a selection or a tapped mark sits, in the pixels of the navigator view. */
data class EpubAnchor(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Is text selected in the book right now? Fed by [EpubSelectionMenu]. */
internal class EpubSelectionTracker {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(on: Boolean) {
        _active.value = on
    }
}

private class EpubPicked(val locatorJson: String, val text: String, val anchor: EpubAnchor?)

/**
 * Floating bar above the selected text (below it when there is no room): the same bar as the PDF reader's
 * ([SelectionToolbar]), with the colour/thickness panel opening on the far side of the bar. A mark is saved at once
 * with the shown colour. Must sit in the same box as the navigator, so its pixels line up.
 */
@Composable
internal fun EpubSelectionPopup(tracker: EpubSelectionTracker, annotations: EpubAnnotationController) {
    val active by tracker.active.collectAsStateWithLifecycle()
    val current by annotations.style.collectAsStateWithLifecycle()
    val tools by annotations.tools.collectAsStateWithLifecycle()
    // the dot and panel show (and change) the look of the tool last used
    val style = tools.lookFor(toolInUse(current))
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<EpubPicked?>(null) }
    var settled by remember { mutableStateOf(false) }
    // the finger may still drag the handles: keep reading where the selection is, but show the bar only once it
    // has stopped moving (a bar chasing the handle, flipping above/below, is the "shake")
    LaunchedEffect(active) {
        picked = null
        settled = false
        if (!active) return@LaunchedEffect
        var still = 0
        while (true) {
            val now = readSelection(activity)
            if (now != null) {
                still = if (sameSpot(picked?.anchor, now.anchor)) still + 1 else 0
                picked = now
            }
            settled = still >= SETTLE_POLLS
            delay(POLL_MS)
        }
    }
    val sel = picked?.takeIf { settled } ?: return
    fun finish(action: () -> Unit) {
        action()
        picked = null
        scope.launch { EpubFragmentHost.navigator(activity)?.clearSelection() }
    }
    val gap = with(LocalDensity.current) { MenuGap.toPx() }
    val actions = SelectionActions(
        onColor = { annotations.setStyle(style.copy(color = it)) },
        onStroke = { annotations.setStyle(style.copy(stroke = it)) },
        onMark = { type -> finish { annotations.markSelection(sel.locatorJson, type) } },
        onCopy = { finish { copyText(activity, sel.text) } },
    )
    SelectionToolbar(style, null, actions) { bar, area -> epubPlacement(sel.anchor, bar, area, gap) }
}

/** True when two selection rects are the same place (within [tol] px); two nulls count as the same. */
internal fun sameSpot(a: EpubAnchor?, b: EpubAnchor?, tol: Float = SPOT_TOL_PX): Boolean {
    if (a == null || b == null) return a == b
    return abs(a.left - b.left) <= tol && abs(a.top - b.top) <= tol &&
        abs(a.right - b.right) <= tol && abs(a.bottom - b.bottom) <= tol
}

/** Full-size transparent box (for clamping) holding one floating surface placed by [epubPopupOrigin]. */
@Composable
internal fun PopupBox(anchor: EpubAnchor?, modifier: Modifier, content: @Composable () -> Unit) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    var menu by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { MenuGap.toPx() }
    Box(Modifier.fillMaxSize().onSizeChanged { box = it }) {
        Surface(
            modifier = modifier
                .onSizeChanged { menu = it }
                .offset { epubPopupOrigin(anchor, menu, box, gap) },
            shape = PanelShape,
            tonalElevation = MenuElevation,
            shadowElevation = MenuElevation,
            content = content,
        )
    }
}

/** [epubPopupOrigin] plus the side the bar ended up on (its colour panel opens on the other side). */
internal fun epubPlacement(anchor: EpubAnchor?, menu: IntSize, box: IntSize, gap: Float): BarPlacement {
    val up = anchor?.let { it.top - menu.height - gap }
    val down = anchor?.let { it.bottom + gap }
    val above = up != null && up >= 0f
    val other = (if (above) down else up)?.takeIf { it >= 0f && it <= box.height - menu.height }?.toInt()
    return BarPlacement(epubPopupOrigin(anchor, menu, box, gap), above, 0, box.height, other)
}

/**
 * Top-left of the popup: centred on [anchor], [gap] above its top; below its bottom when the top would leave the box;
 * no anchor = top centre. Always inside the box.
 */
internal fun epubPopupOrigin(anchor: EpubAnchor?, menu: IntSize, box: IntSize, gap: Float): IntOffset {
    val maxX = (box.width - menu.width).coerceAtLeast(0)
    val maxY = (box.height - menu.height).coerceAtLeast(0)
    if (anchor == null) return IntOffset(maxX / 2, 0)
    val x = ((anchor.left + anchor.right) * HALF - menu.width * HALF).toInt().coerceIn(0, maxX)
    val above = anchor.top - menu.height - gap
    val y = (if (above >= 0f) above else anchor.bottom + gap).toInt().coerceIn(0, maxY)
    return IntOffset(x, y)
}

private suspend fun readSelection(activity: FragmentActivity): EpubPicked? {
    val selection = EpubFragmentHost.navigator(activity)?.currentSelection() ?: return null
    val anchor = selection.rect?.let {
        EpubAnchor(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
    }
    return EpubPicked(encodeLocator(selection.locator), selection.locator.text.highlight.orEmpty(), anchor)
}
