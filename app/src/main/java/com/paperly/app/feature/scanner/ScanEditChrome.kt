package com.paperly.app.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperly.app.R

private const val ARROW_BACKDROP_ALPHA = 0.9f

/** Small upper-case section title ("PAGES", "FILTER") with an optional control on the right. */
@Composable
internal fun SectionHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        trailing()
    }
}

/**
 * The page preview on a rounded card, with previous / next arrows on its sides (only with 2+ pages and not while
 * cropping, where the crop frame needs the whole area).
 */
@Composable
internal fun ColumnScope.EditStage(state: ScanEditUi, viewModel: ScanEditViewModel) {
    val card = if (state.cropping) {
        Modifier
    } else {
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    }
    Box(Modifier.weight(1f).fillMaxWidth().then(card), contentAlignment = Alignment.Center) {
        EditCanvas(state, viewModel)
        if (!state.cropping && !state.awaitingAdd && state.pages.size > 1) PageArrows(state, viewModel)
        if (state.loading || state.saving) ProcessingCard()
    }
}

@Composable
private fun BoxScope.PageArrows(state: ScanEditUi, viewModel: ScanEditViewModel) {
    ArrowButton(
        ScanEditIcons.Previous,
        R.string.scan_edit_prev,
        state.current > 0,
        Modifier.align(Alignment.CenterStart),
    ) { viewModel.goTo(state.current - 1) }
    ArrowButton(
        ScanEditIcons.Next,
        R.string.scan_edit_next,
        state.current < state.pages.size - 1,
        Modifier.align(Alignment.CenterEnd),
    ) { viewModel.goTo(state.current + 1) }
}

@Composable
private fun ArrowButton(icon: ImageVector, label: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = ARROW_BACKDROP_ALPHA),
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp).size(48.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, stringResource(label)) }
    }
}
