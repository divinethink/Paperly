package com.paperly.app.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val MenuGap = 36.dp // the bar sits this far above the selected text (handles hang below it)
private val MenuElevation = 6.dp
private val PanelShape = RoundedCornerShape(16.dp)
private val BarPadding = PaddingValues(horizontal = 8.dp)
private const val POLL_MS = 150L
private const val HALF = 0.5f

// Readium draws only these two as decorations (strikethrough/squiggly would need custom decoration templates)
internal val EPUB_MARK_TYPES = listOf(AnnotationType.HIGHLIGHT, AnnotationType.UNDERLINE)

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
 * Floating bar above the selected text (below it when there is no room), like the PDF reader's: colour dot (opens
 * the six colours), Highlight, Underline, Note, Copy. A mark is saved at once with the shown colour. Must sit in the
 * same box as the navigator, so its pixels line up.
 */
@Composable
internal fun EpubSelectionPopup(tracker: EpubSelectionTracker, annotations: EpubAnnotationController) {
    val active by tracker.active.collectAsStateWithLifecycle()
    val style by annotations.style.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<EpubPicked?>(null) }
    // the finger may still drag the handles: re-read where the selection is while it exists
    LaunchedEffect(active) {
        picked = null
        if (!active) return@LaunchedEffect
        while (true) {
            picked = readSelection(activity)
            delay(POLL_MS)
        }
    }
    val sel = picked ?: return
    fun finish(action: () -> Unit) {
        action()
        picked = null
        scope.launch { EpubFragmentHost.navigator(activity)?.clearSelection() }
    }
    PopupBox(sel.anchor, Modifier) {
        SelectionBar(
            style,
            onColor = { annotations.setStyle(style.copy(color = it)) },
            onMark = { type -> finish { annotations.markSelection(sel.locatorJson, type) } },
            onCopy = { finish { copyText(activity, sel.text) } },
        )
    }
}

@Composable
private fun SelectionBar(
    style: AnnotateStyle,
    onColor: (AnnotationColor) -> Unit,
    onMark: (AnnotationType) -> Unit,
    onCopy: () -> Unit,
) {
    var panel by remember { mutableStateOf(false) } // the six colours, opened by the colour dot
    Column(Modifier.width(IntrinsicSize.Max)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(style.color, panel) { panel = !panel }
            MarkIconRow(selected = null, color = style.color, types = EPUB_MARK_TYPES, onPick = onMark)
            TextButton(onClick = { onMark(AnnotationType.NOTE) }, contentPadding = BarPadding) {
                Text(stringResource(R.string.reader_annotation_note))
            }
            TextButton(onClick = onCopy, contentPadding = BarPadding) {
                Text(stringResource(R.string.reader_select_copy))
            }
        }
        if (panel) ColorRow(style.color, Modifier.fillMaxWidth(), onColor)
    }
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
