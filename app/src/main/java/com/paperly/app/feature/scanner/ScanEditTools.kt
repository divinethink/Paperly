package com.paperly.app.feature.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanFilter
import com.paperly.app.domain.scanner.cropReset
import com.paperly.app.domain.scanner.rotated
import com.paperly.app.domain.scanner.withFilter

private val ThumbWidth = 72.dp
private val ThumbHeight = 92.dp
private val ThumbCorner = 8.dp
private val ThumbBorder = 1.dp
private val ThumbBorderSelected = 2.dp

/** Filter picker: a small preview of every filter on this page; "Apply to all" copies the choice to all pages. */
@Composable
internal fun FilterRow(state: ScanEditUi, page: ScanEditPage, viewModel: ScanEditViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScanFilter.entries.forEach { filter ->
            FilterThumb(
                label = stringResource(filterLabel(filter)),
                thumb = state.thumbs[filter],
                selected = page.filter == filter,
                onClick = { viewModel.edit { it.withFilter(filter) } },
            )
        }
    }
    if (state.pages.size > 1) {
        TextButton(onClick = viewModel::applyFilterToAll, enabled = !state.saving) {
            Text(stringResource(R.string.scan_filter_apply_all))
        }
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
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
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
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

/** Previous / rotate / crop / reset / next as icon buttons (each has a spoken label). */
@Composable
internal fun ToolRow(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val page = state.page ?: return
    val hasNext = state.current < state.pages.size - 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ToolButton(ScanEditIcons.Previous, R.string.scan_edit_prev, state.current > 0) {
            viewModel.goTo(state.current - 1)
        }
        ToolButton(ScanEditIcons.Rotate, R.string.scan_edit_rotate, !page.locked) {
            viewModel.edit(ScanEditPage::rotated)
        }
        ToolButton(
            if (state.cropping) ScanEditIcons.Done else ScanEditIcons.Crop,
            if (state.cropping) R.string.scan_edit_crop_done else R.string.scan_edit_crop,
            !page.locked,
            viewModel::toggleCrop,
        )
        ToolButton(ScanEditIcons.Reset, R.string.scan_edit_reset, !page.locked) {
            viewModel.edit(ScanEditPage::cropReset)
        }
        ToolButton(ScanEditIcons.Next, R.string.scan_edit_next, hasNext) { viewModel.goTo(state.current + 1) }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
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
    val Done: ImageVector by lazy { glyph("ScanDone", DONE_PATH) }
}
