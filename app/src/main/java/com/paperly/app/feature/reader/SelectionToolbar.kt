package com.paperly.app.feature.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotateStyle
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType

private val PanelPadding = 12.dp
private val PanelBottom = 8.dp
private val PanelGap = 4.dp
private val BarSide = 4.dp
private val DividerGap = 2.dp
private val DividerHeight = 24.dp
private val ActionHeight = 42.dp // as tall as the icon buttons
private val ActionPadding = 8.dp

/**
 * The bar (colour dot | four mark icons | Note | Copy) and, under the dot, the colour + thickness panel. The panel
 * opens on the side of the bar away from the text ([FloatingToolbar]), so it never covers the marked line.
 * [place] puts the bar next to what it acts on.
 */
@Composable
internal fun SelectionToolbar(
    style: AnnotateStyle,
    marked: AnnotationType?,
    actions: SelectionActions,
    place: (bar: IntSize, area: IntSize) -> BarPlacement,
) {
    var panel by remember { mutableStateOf(false) } // colour + thickness, opened by the colour dot
    FloatingToolbar(
        panelOpen = panel,
        place = place,
        panel = { SelectionPanel(style, actions) },
        bar = { SelectionBarRow(style, marked, panel, actions) { panel = !panel } },
    )
}

@Composable
private fun SelectionBarRow(
    style: AnnotateStyle,
    marked: AnnotationType?,
    panelOpen: Boolean,
    actions: SelectionActions,
    onDot: () -> Unit,
) {
    Row(Modifier.padding(horizontal = BarSide), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(style.color, panelOpen, onDot)
        BarDivider()
        MarkIconRow(selected = marked, color = style.color, onPick = actions.onMark)
        BarDivider()
        TextAction(R.string.reader_annotation_note) { actions.onMark(AnnotationType.NOTE) }
        BarDivider()
        TextAction(R.string.reader_select_copy, actions.onCopy)
    }
}

@Composable
private fun SelectionPanel(style: AnnotateStyle, actions: SelectionActions) {
    Column(Modifier.padding(bottom = PanelBottom)) {
        ColorRow(style.color, Modifier.fillMaxWidth().padding(vertical = PanelGap), actions.onColor)
        // a highlight has no line, so no thickness to choose
        if (AnnotationStroke.appliesTo(style.type)) {
            StrokeRow(
                style.stroke,
                style.color,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelPadding)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                actions.onStroke,
            )
        }
    }
}

/** Text button as tight as the icons (a Material TextButton is at least 58 dp wide, which spread Note and Copy). */
@Composable
private fun TextAction(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(ActionHeight)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ActionPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Thin separator between the groups of the bar (colour | marks | Note, Copy). */
@Composable
private fun BarDivider() {
    Box(
        Modifier
            .padding(horizontal = DividerGap)
            .width(1.dp)
            .height(DividerHeight)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
