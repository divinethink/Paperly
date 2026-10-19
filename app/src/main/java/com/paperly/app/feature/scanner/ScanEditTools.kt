package com.paperly.app.feature.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.scanner.MAX_BRIGHTNESS
import com.paperly.app.domain.scanner.MAX_CONTRAST
import com.paperly.app.domain.scanner.MIN_BRIGHTNESS
import com.paperly.app.domain.scanner.MIN_CONTRAST
import com.paperly.app.domain.scanner.SCAN_FILTER_CHOICES
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.adjustReset
import com.paperly.app.domain.scanner.withBrightness
import com.paperly.app.domain.scanner.withContrast
import com.paperly.app.domain.scanner.withFilter
import kotlin.math.abs
import kotlin.math.roundToInt

private val ThumbWidth = 56.dp
private val ThumbHeight = 72.dp
private val BW_SIZE = 48.dp
private val ThumbCorner = 8.dp
private val ThumbBorder = 1.dp
private val ThumbBorderSelected = 2.dp
private val PAGE_CHIP_SIZE = 40.dp
private val PAGE_CHIP_GAP = 6.dp
private val ADJUST_LABEL_WIDTH = 88.dp
private val ADJUST_VALUE_WIDTH = 36.dp

/**
 * Filter picker: Original / Photo / Docs / Auto previews plus a separate B/W toggle; "Apply to all" copies the
 * choice to all pages.
 */
@Composable
internal fun FilterRow(state: ScanEditUi, page: ScanEditPage, viewModel: ScanEditViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SCAN_FILTER_CHOICES.forEach { filter ->
            FilterThumb(
                label = stringResource(filterLabel(filter)),
                thumb = state.thumbs[filter],
                selected = page.filter == filter,
                onClick = { viewModel.edit { it.withFilter(filter) } },
            )
        }
        BwToggle(page.filter == ScanFilter.BW, viewModel::toggleBw)
    }
    if (state.pages.size > 1) {
        TextButton(onClick = viewModel::applyFilterToAll, enabled = !state.saving, colors = actionTextColors()) {
            Text(stringResource(R.string.scan_filter_apply_all))
        }
    }
}

/** Black-and-white on/off; turning it off returns to the filter the page had before. */
@Composable
private fun BwToggle(selected: Boolean, onToggle: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(BW_SIZE)
            .border(if (selected) ThumbBorderSelected else ThumbBorder, color, CircleShape)
            .clip(CircleShape)
            .toggleable(value = selected, role = Role.Switch, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.scan_filter_bw),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FilterThumb(
    label: String,
    thumb: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(ThumbCorner)
    val color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.width(ThumbWidth).selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(ThumbWidth).height(ThumbHeight)
                .border(if (selected) ThumbBorderSelected else ThumbBorder, color, shape)
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb, label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun filterLabel(filter: ScanFilter): Int = when (filter) {
    ScanFilter.ORIGINAL -> R.string.scan_filter_original
    ScanFilter.COLOR -> R.string.scan_filter_color
    ScanFilter.GRAY -> R.string.scan_filter_gray
    ScanFilter.BW -> R.string.scan_filter_bw
    ScanFilter.DOCS -> R.string.scan_filter_docs
    ScanFilter.AUTO -> R.string.scan_filter_auto
    ScanFilter.PHOTO -> R.string.scan_filter_photo
}

/** Previous page, "Page N of M", next page (rotate / crop / reset / adjust are in the bottom bar). */
@Composable
internal fun ToolRow(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val hasNext = state.current < state.pages.size - 1
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(ScanEditIcons.Previous, R.string.scan_edit_prev, state.current > 0) {
            viewModel.goTo(state.current - 1)
        }
        Text(
            stringResource(R.string.scan_edit_page_of, state.current + 1, state.pages.size.coerceAtLeast(1)),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        ToolButton(ScanEditIcons.Next, R.string.scan_edit_next, hasNext) { viewModel.goTo(state.current + 1) }
    }
}

/** Manual brightness and contrast of the shown page (saved with the page; "Reset" puts both back to 0). */
@Composable
internal fun AdjustPanel(page: ScanEditPage, viewModel: ScanEditViewModel) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        AdjustSlider(R.string.scan_adjust_brightness, page.brightness, MIN_BRIGHTNESS..MAX_BRIGHTNESS) { value ->
            viewModel.edit { it.withBrightness(value) }
        }
        AdjustSlider(R.string.scan_adjust_contrast, page.contrast, MIN_CONTRAST..MAX_CONTRAST) { value ->
            viewModel.edit { it.withContrast(value) }
        }
        TextButton(
            onClick = { viewModel.edit(ScanEditPage::adjustReset) },
            enabled = page.brightness != 0 || page.contrast != 0,
            colors = actionTextColors(),
        ) { Text(stringResource(R.string.scan_adjust_reset)) }
    }
}

@Composable
private fun AdjustSlider(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(ADJUST_LABEL_WIDTH), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), Modifier.width(ADJUST_VALUE_WIDTH), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Page numbers: tap = open that page; press and hold, then drag sideways = move the page (one place per chip width).
 * Same rules as the "Move earlier / later" buttons, which stay as the non-drag way.
 */
@Composable
internal fun PageStrip(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val step = with(LocalDensity.current) { (PAGE_CHIP_SIZE + PAGE_CHIP_GAP).toPx() }
    val current by rememberUpdatedState(state.current)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PAGE_CHIP_GAP)) {
        state.pages.forEachIndexed { index, _ ->
            val selected = index == state.current
            val color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
            val label = stringResource(R.string.scan_edit_page_number, index + 1)
            Box(
                Modifier.size(PAGE_CHIP_SIZE)
                    .border(if (selected) ThumbBorderSelected else ThumbBorder, color, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = label) { viewModel.goTo(index) }
                    .pointerInput(index) {
                        var carried = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                carried = 0f
                                if (index != current) viewModel.goTo(index)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                carried += drag.x
                                while (abs(carried) >= step) {
                                    val direction = if (carried > 0f) 1 else -1
                                    viewModel.move(direction)
                                    carried -= direction * step
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Text(stringResource(R.string.scan_edit_reorder_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun ToolButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, stringResource(label)) }
}

// 24dp Material-style glyphs from path data: the bundled icon set (icons-core) has no crop/rotate, and the
// extended set would add a very large dependency (same approach as navigation/NavIcons.kt).
private const val ICON_SIZE = 24f

private const val ROTATE_PATH =
    "M15.55,5.55L11,1v3.07C7.06,4.56 4,7.92 4,12s3.05,7.44 7,7.93v-2.02c-2.84,-0.48 -5,-2.94 -5,-5.91" +
        "s2.16,-5.43 5,-5.91V10l4.55,-4.45zM19.93,11c-0.17,-1.39 -0.72,-2.73 -1.62,-3.89l-1.42,1.42" +
        "c0.54,0.75 0.88,1.6 1.02,2.47h2.02zM13,17.9v2.02c1.39,-0.17 2.74,-0.71 3.9,-1.61l-1.44,-1.44" +
        "c-0.75,0.54 -1.59,0.89 -2.46,1.03zM16.89,15.48l1.42,1.41c0.9,-1.16 1.45,-2.5 1.62,-3.89h-2.02" +
        "c-0.14,0.87 -0.48,1.72 -1.02,2.48z"
private const val CROP_PATH =
    "M17,15h2V7c0,-1.1 -0.9,-2 -2,-2H9v2h8v8zM7,17V1H5v4H1v2h4v10c0,1.1 0.9,2 2,2h10v4h2v-4h4v-2H7z"
private const val RESET_PATH =
    "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8" +
        " -3.58,-8 -8,-8z"
private const val PREVIOUS_PATH = "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z"
private const val NEXT_PATH = "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"
private const val ADJUST_PATH =
    "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10z" +
        "M15,9h2V7h4V5h-4V3h-2v6z"
private const val DONE_PATH = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"

private fun glyph(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE.dp,
        defaultHeight = ICON_SIZE.dp,
        viewportWidth = ICON_SIZE,
        viewportHeight = ICON_SIZE,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

internal object ScanEditIcons {
    val Rotate: ImageVector by lazy { glyph("ScanRotate", ROTATE_PATH) }
    val Crop: ImageVector by lazy { glyph("ScanCrop", CROP_PATH) }
    val Reset: ImageVector by lazy { glyph("ScanReset", RESET_PATH) }
    val Previous: ImageVector by lazy { glyph("ScanPrevious", PREVIOUS_PATH) }
    val Next: ImageVector by lazy { glyph("ScanNext", NEXT_PATH) }
    val Adjust: ImageVector by lazy { glyph("ScanAdjust", ADJUST_PATH) }
    val Done: ImageVector by lazy { glyph("ScanDone", DONE_PATH) }
}
