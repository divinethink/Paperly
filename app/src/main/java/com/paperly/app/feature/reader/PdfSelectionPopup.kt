package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.lookFor

private val MenuGap = 10.dp // at least; the menu sits about one text line above the selection
private val HandleClearance = 36.dp // below the last line sit the round selection handles
private const val LINES_ABOVE = 2f
private const val MAX_GAP_FACTOR = 1.5f // two lines of a tall rect must not push the menu far away: cap at 1.5 x gap
private const val HALF = 0.5f
private const val PANEL_RESERVE_FACTOR = 2.8f // the open colour panel is about 2.7 bars tall (122 dp vs 46 dp)

/** What the popup needs to turn page fractions into screen pixels: list scroll, zoom, page width, viewport size. */
internal class SelectionViewport(
    val listState: LazyListState,
    val zoom: PageZoomState,
    val basePx: Float,
    val viewportPx: Float,
    /** Bottom of the usable page area (above the navigation bar): a menu never goes below it. */
    val heightPx: Float,
    /** Height of the floating top bar + status bar: a menu never goes under it. */
    val topPx: Float = 0f,
)

/** What the bar can do; bundled so the bar composable stays within the detekt parameter limit. */
internal class SelectionActions(
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
    val tools by viewModel.annotations.tools.collectAsStateWithLifecycle()
    val dragging by viewModel.textSelect.dragging.collectAsStateWithLifecycle()
    val items by viewModel.annotations.items.collectAsStateWithLifecycle()
    val picked = selection
    val hidden = annotate || reflow || dragging
    if (picked == null || hidden) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val gap = with(density) { MenuGap.toPx() }
    val clearance = with(density) { HandleClearance.toPx() }
    // the marks just placed on exactly this selection (one per page, still stored): colour/type changes restyle
    // them all, not add more
    val marks = items.filter { it.id in picked.markIds }
    // the dot and panel show (and change) the look of the tool in use: the mark's own, else the last one used
    val tool = marks.firstOrNull()?.type ?: toolInUse(style)
    val look = tools.lookFor(tool)
    val actions = selectionActions(SelectionContext(viewModel, context, picked, marks, tools), look, onEditor)
    SelectionToolbar(look, marks.firstOrNull()?.type, actions) { bar, _ ->
        popupPlacement(picked, port, bar, gap, clearance)
    }
}

/** What the bar's actions work on: the selection, the marks already placed on it and each tool's own look. */
private class SelectionContext(
    val viewModel: ReaderViewModel,
    val context: Context,
    val picked: PdfSelection,
    val marks: List<Annotation>,
    val tools: Map<AnnotationType, AnnotateStyle>,
)

private fun selectionActions(
    ctx: SelectionContext,
    look: AnnotateStyle,
    onEditor: (AnnotationEditor) -> Unit,
): SelectionActions {
    val controller = ctx.viewModel.annotations
    fun restyleAll(change: (AnnotationContent) -> AnnotationContent) =
        ctx.marks.forEach { controller.restyleMark(it.id, change) }
    fun annotateAs(type: AnnotationType) {
        val own = ctx.tools.lookFor(type) // each tool keeps its own colour and thickness
        if (type != AnnotationType.NOTE) controller.setStyle(own)
        if (ctx.marks.isNotEmpty() && type != AnnotationType.NOTE) {
            restyleAll { it.copy(type = type, color = own.color, stroke = own.stroke) }
            return
        }
        val editor = controller.annotateSelection(ctx.picked.parts, type) { ids ->
            ctx.viewModel.textSelect.linkMark(ctx.picked, ids) // the selection stays: colour/type can still change
        }
        if (editor != null) {
            onEditor(editor)
            ctx.viewModel.textSelect.clear()
        }
    }
    return SelectionActions(
        onColor = { color ->
            controller.setStyle(look.copy(color = color))
            restyleAll { c -> c.copy(color = color) }
        },
        onStroke = { stroke ->
            controller.setStyle(look.copy(stroke = stroke))
            restyleAll { c -> c.copy(stroke = stroke) }
        },
        onMark = ::annotateAs,
        onCopy = {
            copyText(ctx.context, ctx.picked.text)
            ctx.viewModel.textSelect.clear()
        },
    )
}

/** The tool the dot/panel stand for when no mark is picked: the last one used (a Note has no colour: Highlight). */
internal fun toolInUse(style: AnnotateStyle): AnnotationType =
    style.type.takeUnless { it == AnnotationType.NOTE } ?: AnnotationType.HIGHLIGHT

/**
 * Where the bar goes, in viewport px: centred on the selection, above its
 * first line (first part), or below the last line (last part) when the top would leave the viewport. A selection
 * scrolled out of view parks the bar at the top edge (its panel then opens below it). When the open colour panel would
 * not fit above the bar either, the bar goes below the text from the start, so opening the panel never makes the bar
 * jump to the other side or the panel cover the marked line.
 */
internal fun popupPlacement(
    sel: PdfSelection,
    port: SelectionViewport,
    menu: IntSize,
    gap: Float,
    clearance: Float,
): BarPlacement {
    val zoom = port.zoom.zoom
    val visible = port.listState.layoutInfo.visibleItemsInfo
    val firstPart = sel.parts.firstOrNull()
    val lastPart = sel.parts.lastOrNull()
    val first = firstPart?.rects?.firstOrNull()
    val last = lastPart?.rects?.lastOrNull()
    val minY = port.topPx
    val maxY = (port.heightPx - menu.height).coerceAtLeast(minY)
    val maxX = (port.viewportPx - menu.width).coerceAtLeast(0f)
    // item offsets start below the list's top content padding (the top bar); the bar's place is in container px
    val pad = port.listState.layoutInfo.beforeContentPadding
    val firstItem = visible.firstOrNull { it.index == firstPart?.page }
    val lastItem = visible.firstOrNull { it.index == lastPart?.page }
    val above = if (firstItem != null && first != null) {
        // clear of the selected line by about two line heights (never less than [gap])
        val lines = (first.bottom - first.top) * firstItem.size * zoom * LINES_ABOVE
        val clear = minOf(maxOf(gap, lines), gap * MAX_GAP_FACTOR)
        (firstItem.offset + pad + first.top * firstItem.size) * zoom - menu.height - clear
    } else {
        null
    }
    val below = if (lastItem != null && last != null) {
        (lastItem.offset + pad + last.bottom * lastItem.size) * zoom + clearance
    } else {
        null
    }
    val top = minY.toInt()
    val bottom = port.heightPx.toInt()
    if (above == null && below == null) return BarPlacement(IntOffset((maxX * HALF).toInt(), top), false, top, bottom)
    val rects = sel.parts.flatMap { it.rects }
    val left = rects.minOf { it.left }
    val right = rects.maxOf { it.right }
    val centre = (left + right) * HALF * port.basePx * zoom + port.zoom.offsetX(port.viewportPx, port.basePx)
    val x = (centre - menu.width * HALF).coerceIn(0f, maxX).toInt()
    val barAbove = popupAbove(above, below, minY, menu.height * PANEL_RESERVE_FACTOR)
    val y = (if (barAbove && above != null) above else below ?: minY).coerceIn(minY, maxY).toInt()
    // the other side of the text, only when that side is on screen and the bar fits there
    val other = (if (barAbove) below else above)?.takeIf { it in minY..maxY }?.toInt()
    return BarPlacement(IntOffset(x, y), barAbove, top, bottom, other)
}

/** Top-left of the bar only (for popups that have no colour panel): [popupPlacement] without the side. */
internal fun popupOrigin(
    sel: PdfSelection,
    port: SelectionViewport,
    menu: IntSize,
    gap: Float,
    clearance: Float,
): IntOffset = popupPlacement(sel, port, menu, gap, clearance).origin

/** Whether the bar sits above the text: the first line is on screen and has room (plus [reserve]) under the top bar. */
internal fun popupAbove(above: Float?, below: Float?, minY: Float = 0f, reserve: Float = 0f): Boolean =
    above != null && (above - reserve >= minY || below == null)

/** Above the first line when it fits on screen, else below the last line; whichever end is off screen is null. */
internal fun popupY(above: Float?, below: Float?, maxY: Float, minY: Float = 0f): Float =
    (if (above != null && (above >= minY || below == null)) above else below ?: minY).coerceIn(minY, maxY)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}
