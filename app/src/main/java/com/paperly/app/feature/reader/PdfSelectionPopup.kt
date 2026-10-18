package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType

private val MenuGap = 36.dp // at least; the menu sits about two text lines above the selection
private val HandleClearance = 36.dp // below the last line sit the round selection handles
private val MenuElevation = 6.dp
private val PanelPadding = 12.dp
private val PanelShape = RoundedCornerShape(16.dp)
private const val LINES_ABOVE = 2f
private val BarPadding = PaddingValues(horizontal = 8.dp)
private const val HALF = 0.5f

/** What the popup needs to turn page fractions into screen pixels: list scroll, zoom, page width, viewport size. */
internal class SelectionViewport(
    val listState: LazyListState,
    val zoom: PageZoomState,
    val basePx: Float,
    val viewportPx: Float,
    val heightPx: Float,
)

/** What the bar can do; bundled so the bar composable stays within the detekt parameter limit. */
private class SelectionActions(
    val onColor: (AnnotationColor) -> Unit,
    val onStroke: (AnnotationStroke) -> Unit,
    val onMark: (AnnotationType) -> Unit,
    val onCopy: () -> Unit,
)

/**
 * Floating one-row bar right above the selected text (below it when there is no room), inside the page area:
 * colour dot, Highlight / Underline / Squiggly / Strikethrough one-tap icons, Note, Copy. The dot opens a colour and
 * thickness panel; a mark saves with what is shown. Hidden in annotate mode, Reflow and while a finger is down on
 * the selection (only the magnifier shows then); it returns when the finger lifts.
 */
@Composable
internal fun PdfSelectionPopup(
    viewModel: ReaderViewModel,
    port: SelectionViewport,
    onEditor: (AnnotationEditor) -> Unit,
) {
    val selection by viewModel.textSelect.state.collectAsStateWithLifecycle()
    val annotate by viewModel.annotations.annotateMode.collectAsStateWithLifecycle()
    val reflow by viewModel.reflow.collectAsStateWithLifecycle()
    val style by viewModel.annotations.style.collectAsStateWithLifecycle()
    val dragging by viewModel.textSelect.dragging.collectAsStateWithLifecycle()
    val items by viewModel.annotations.items.collectAsStateWithLifecycle()
    val picked = selection
    val hidden = annotate || reflow || dragging
    if (picked == null || hidden) return
    val context = LocalContext.current
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { MenuGap.toPx() }
    val clearance = with(density) { HandleClearance.toPx() }
    // the marks just placed on exactly this selection (one per page, still stored): colour/type changes restyle
    // them all, not add more
    val marks = items.filter { it.id in picked.markIds }
    val mark = marks.firstOrNull()
    fun restyleAll(change: (AnnotationContent) -> AnnotationContent) =
        marks.forEach { viewModel.annotations.restyleMark(it.id, change) }
    fun annotateAs(type: AnnotationType) {
        if (mark != null && type != AnnotationType.NOTE) {
            restyleAll { it.copy(type = type, color = style.color, stroke = style.stroke) }
            return
        }
        val editor = viewModel.annotations.annotateSelection(picked.parts, type) { ids ->
            viewModel.textSelect.linkMark(picked, ids) // the selection stays, so colour and type can still be changed
        }
        if (editor != null) {
            onEditor(editor)
            viewModel.textSelect.clear()
        }
    }
    val actions = SelectionActions(
        onColor = { color ->
            viewModel.annotations.setStyle(style.copy(color = color))
            restyleAll { c -> c.copy(color = color) }
        },
        onStroke = { stroke ->
            viewModel.annotations.setStyle(style.copy(stroke = stroke))
            restyleAll { c -> c.copy(stroke = stroke) }
        },
        onMark = ::annotateAs,
        onCopy = {
            copyText(context, picked.text)
            viewModel.textSelect.clear()
        },
    )
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .onSizeChanged { menu = it }
                .offset { popupOrigin(picked, port, menu, gap, clearance) },
            shape = PanelShape,
            tonalElevation = MenuElevation,
            shadowElevation = MenuElevation,
        ) {
            SelectionBar(style, mark?.type, actions)
        }
    }
}

@Composable
private fun SelectionBar(
    style: AnnotateStyle,
    marked: AnnotationType?,
    actions: SelectionActions,
) {
    var panel by remember { mutableStateOf(false) } // colour + thickness, opened by the colour dot
    Column(Modifier.width(IntrinsicSize.Max)) { // the panel is exactly as wide as the bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(style.color, panel) { panel = !panel }
            MarkIconRow(selected = marked, color = style.color, onPick = actions.onMark)
            TextButton(onClick = { actions.onMark(AnnotationType.NOTE) }, contentPadding = BarPadding) {
                Text(stringResource(R.string.reader_annotation_note))
            }
            TextButton(onClick = actions.onCopy, contentPadding = BarPadding) {
                Text(stringResource(R.string.reader_select_copy))
            }
        }
        if (panel) {
            ColorRow(style.color, Modifier.fillMaxWidth(), actions.onColor)
            StrokeRow(
                style.stroke,
                style.color,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelPadding, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                actions.onStroke,
            )
        }
    }
}

/**
 * Top-left of the menu in viewport px: centred on the selection, above its first line (first part), or below the last
 * line (last part) when the top would leave the viewport. A selection scrolled out of view parks the menu at the top
 * edge.
 */
internal fun popupOrigin(
    sel: PdfSelection,
    port: SelectionViewport,
    menu: IntSize,
    gap: Float,
    clearance: Float,
): IntOffset {
    val zoom = port.zoom.zoom
    val visible = port.listState.layoutInfo.visibleItemsInfo
    val firstPart = sel.parts.firstOrNull()
    val lastPart = sel.parts.lastOrNull()
    val first = firstPart?.rects?.firstOrNull()
    val last = lastPart?.rects?.lastOrNull()
    val maxY = (port.heightPx - menu.height).coerceAtLeast(0f)
    val maxX = (port.viewportPx - menu.width).coerceAtLeast(0f)
    val firstItem = visible.firstOrNull { it.index == firstPart?.page }
    val lastItem = visible.firstOrNull { it.index == lastPart?.page }
    val above = if (firstItem != null && first != null) {
        // clear of the selected line by about two line heights (never less than [gap])
        val lines = (first.bottom - first.top) * firstItem.size * zoom * LINES_ABOVE
        (firstItem.offset + first.top * firstItem.size) * zoom - menu.height - maxOf(gap, lines)
    } else {
        null
    }
    val below = if (lastItem != null && last != null) {
        (lastItem.offset + last.bottom * lastItem.size) * zoom + clearance
    } else {
        null
    }
    if (above == null && below == null) return IntOffset((maxX * HALF).toInt(), 0)
    val rects = sel.parts.flatMap { it.rects }
    val left = rects.minOf { it.left }
    val right = rects.maxOf { it.right }
    val centre = (left + right) * HALF * port.basePx * zoom + port.zoom.offsetX(port.viewportPx, port.basePx)
    val x = (centre - menu.width * HALF).coerceIn(0f, maxX)
    return IntOffset(x.toInt(), popupY(above, below, maxY).toInt())
}

/** Above the first line when it fits on screen, else below the last line; whichever end is off screen is null. */
internal fun popupY(above: Float?, below: Float?, maxY: Float): Float =
    (if (above != null && (above >= 0f || below == null)) above else below ?: 0f).coerceIn(0f, maxY)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}
