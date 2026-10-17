package com.paperly.app.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType

private val MenuGap = 8.dp
private val HandleClearance = 36.dp // below the last line sit the round selection handles
private val MenuElevation = 6.dp
private val SwatchWidth = 44.dp
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
    val onMark: (AnnotationType) -> Unit,
    val onCopy: () -> Unit,
)

/**
 * Floating bar right above the selected text (below it when there is no room), inside the page area: colour row,
 * then Highlight / Underline / Squiggly / Strikethrough one-tap icons, Note and Copy. A mark saves with the colour
 * shown; hidden in annotate mode and Reflow.
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
    val search by viewModel.search.state.collectAsStateWithLifecycle()
    val style by viewModel.annotations.style.collectAsStateWithLifecycle()
    val picked = selection
    if (picked == null || annotate || reflow) return
    val context = LocalContext.current
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(density) { MenuGap.toPx() }
    val clearance = with(density) { HandleClearance.toPx() }
    fun annotateAs(type: AnnotationType) {
        viewModel.annotations.annotateSelection(picked.page, picked.rects, type)?.let(onEditor)
        viewModel.textSelect.clear()
    }
    val actions = SelectionActions(
        onColor = { viewModel.annotations.setStyle(style.copy(color = it)) },
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
            shape = MaterialTheme.shapes.large,
            tonalElevation = MenuElevation,
            shadowElevation = MenuElevation,
        ) {
            // Bengali extraction drops vowel signs: never offer a copy of broken text
            SelectionBar(style.color, canCopy = !search.partialMatch, actions)
        }
    }
}

@Composable
private fun SelectionBar(color: AnnotationColor, canCopy: Boolean, actions: SelectionActions) {
    Column {
        Row {
            AnnotationColor.entries.forEach { option ->
                ColorSwatch(option, option == color, Modifier.width(SwatchWidth)) { actions.onColor(option) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkIconRow(selected = null, color = color, onPick = actions.onMark)
            TextButton(onClick = { actions.onMark(AnnotationType.NOTE) }) {
                Text(stringResource(R.string.reader_annotation_note))
            }
            if (canCopy) {
                TextButton(onClick = actions.onCopy) { Text(stringResource(R.string.reader_select_copy)) }
            }
        }
    }
}

/**
 * Top-left of the menu in viewport px: centred on the selection, above its first line, or below the last line when
 * the top would leave the viewport. A page scrolled out of view parks the menu at the top edge.
 */
internal fun popupOrigin(
    sel: PdfSelection,
    port: SelectionViewport,
    menu: IntSize,
    gap: Float,
    clearance: Float,
): IntOffset {
    val zoom = port.zoom.zoom
    val item = port.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == sel.page }
    val first = sel.rects.firstOrNull()
    val last = sel.rects.lastOrNull()
    val maxY = (port.heightPx - menu.height).coerceAtLeast(0f)
    val maxX = (port.viewportPx - menu.width).coerceAtLeast(0f)
    if (item == null || first == null || last == null) return IntOffset((maxX * HALF).toInt(), 0)
    val above = (item.offset + first.top * item.size) * zoom - menu.height - gap
    val below = (item.offset + last.bottom * item.size) * zoom + clearance
    val left = sel.rects.minOf { it.left }
    val right = sel.rects.maxOf { it.right }
    val centre = (left + right) * HALF * port.basePx * zoom + port.zoom.offsetX(port.viewportPx, port.basePx)
    val x = (centre - menu.width * HALF).coerceIn(0f, maxX)
    val y = (if (above >= 0f) above else below).coerceIn(0f, maxY)
    return IntOffset(x.toInt(), y.toInt())
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}
