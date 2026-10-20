package com.paperly.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.cropReset
import com.paperly.app.domain.scanner.rotated

/** Brown text action (Notebloc style) for the buttons on the white area. */
@Composable
internal fun actionTextColors() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)

/**
 * Bottom teal bar of the editor: reset crop · rotate · crop on/off · adjust. It paints under the navigation bar
 * itself. The buttons run the same actions as before (only moved here).
 */
@Composable
internal fun EditBottomBar(state: ScanEditUi, viewModel: ScanEditViewModel, onAdjust: () -> Unit) {
    val page = state.page ?: return
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolItem(ScanEditIcons.Reset, R.string.scan_edit_reset, !page.locked) {
                viewModel.edit(ScanEditPage::cropReset)
            }
            ToolItem(ScanEditIcons.Rotate, R.string.scan_edit_rotate, !page.locked) {
                viewModel.edit(ScanEditPage::rotated)
            }
            ToolItem(
                if (state.cropping) ScanEditIcons.Done else ScanEditIcons.Crop,
                if (state.cropping) R.string.scan_edit_crop_done else R.string.scan_edit_crop,
                !page.locked,
                viewModel::toggleCrop,
            )
            ToolItem(ScanEditIcons.Adjust, R.string.scan_adjust, true, onAdjust)
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
private val ToolItemMinHeight = 56.dp

/** Icon with its name underneath; the whole cell is the touch target. */
@Composable
private fun RowScope.ToolItem(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
    Column(
        Modifier.weight(1f)
            .heightIn(min = ToolItemMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** White "Processing…" card in the middle while pages load or save. */
@Composable
internal fun ProcessingCard() {
    Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                stringResource(R.string.scan_edit_processing),
                Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
