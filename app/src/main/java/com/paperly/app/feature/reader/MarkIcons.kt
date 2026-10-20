package com.paperly.app.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperly.app.R
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType

private val MarkButton = 42.dp
private val DotSize = 22.dp
private val StrokeCell = 64.dp
private val StrokePreview = 36.dp
private const val PREVIEW_SCALE = 1.6f // the 0.8 dp thin line would be nearly invisible at true size
private val ChipSize = 32.dp
private const val RING_ALPHA = 0.25f // outline of the colours that are not chosen
private const val LIGHT_COLOR = 0.5f // above this luminance the tick is drawn dark
private val MarkGlyph = 24.dp
private val MarkStroke = 2.dp
private const val FILL_ALPHA = 0.45f
private const val UNDER_AT = 0.92f
private const val MIDDLE_AT = 0.5f

/** The four text-mark types, in toolbar order. */
internal val MARK_TYPES = listOf(
    AnnotationType.HIGHLIGHT,
    AnnotationType.UNDERLINE,
    AnnotationType.SQUIGGLY,
    AnnotationType.STRIKETHROUGH,
)

/**
 * One-tap mark buttons (Highlight, Underline, Squiggly, Strikethrough): a letter "A" drawn with the mark in the
 * [color] it will get, so the toolbar previews the result. [selected] outlines the type an annotation already has.
 */
@Composable
internal fun MarkIconRow(
    selected: AnnotationType?,
    color: AnnotationColor,
    modifier: Modifier = Modifier,
    types: List<AnnotationType> = MARK_TYPES,
    onPick: (AnnotationType) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        types.forEach { type -> MarkIcon(type, color.argb(), type == selected) { onPick(type) } }
    }
}

@Composable
private fun MarkIcon(type: AnnotationType, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(typeLabel(type))
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Box(
        Modifier
            .size(MarkButton)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MarkGlyph)) { drawMark(type, tint) }
        Text("A", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun DrawScope.drawMark(type: AnnotationType, tint: Color) {
    val stroke = MarkStroke.toPx()
    fun line(at: Float) = drawLine(tint, Offset(0f, size.height * at), Offset(size.width, size.height * at), stroke)
    when (type) {
        AnnotationType.HIGHLIGHT -> drawRect(tint.copy(alpha = FILL_ALPHA))
        AnnotationType.SQUIGGLY -> drawSquiggle(tint, 0f, size.height * UNDER_AT, size.width, stroke / 2)
        AnnotationType.STRIKETHROUGH -> line(MIDDLE_AT)
        else -> line(UNDER_AT)
    }
}

/** Round colour button of the toolbar: shows the colour the next mark gets; [open] = the colour/thickness panel. */
@Composable
internal fun ColorDot(color: AnnotationColor, open: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.reader_annotation_color)
    val background = if (open) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Box(
        Modifier
            .size(MarkButton)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(DotSize).clip(CircleShape).background(color.argb()))
    }
}

/** Thin / Medium / Thick: each button previews its line in the [color] it will get. */
@Composable
internal fun StrokeRow(
    selected: AnnotationStroke,
    color: AnnotationColor,
    modifier: Modifier = Modifier,
    onPick: (AnnotationStroke) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        AnnotationStroke.entries.forEach { option ->
            StrokeButton(option, color.argb(), option == selected) { onPick(option) }
        }
    }
}

@Composable
private fun StrokeButton(stroke: AnnotationStroke, tint: Color, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(strokeLabel(stroke))
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = StrokeCell)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.width(StrokePreview).height(8.dp)) {
            val y = size.height / 2
            drawLine(tint, Offset(0f, y), Offset(size.width, y), (stroke.dp * PREVIEW_SCALE).dp.toPx())
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun strokeLabel(stroke: AnnotationStroke): Int = when (stroke) {
    AnnotationStroke.THIN -> R.string.reader_annotation_stroke_thin
    AnnotationStroke.MEDIUM -> R.string.reader_annotation_stroke_medium
    AnnotationStroke.THICK -> R.string.reader_annotation_stroke_thick
}

/** Six colour circles; the chosen one carries a tick (dark on light colours, white on dark ones). */
@Composable
internal fun ColorRow(selected: AnnotationColor, modifier: Modifier = Modifier, onPick: (AnnotationColor) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        AnnotationColor.entries.forEach { option ->
            val name = stringResource(colorLabel(option))
            val on = option == selected
            val tick = if (option.argb().luminance() > LIGHT_COLOR) Color.Black else Color.White
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = on, onClick = { onPick(option) }, role = Role.RadioButton)
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = if (on) 1f else RING_ALPHA)
                Box(
                    Modifier
                        .size(ChipSize)
                        .clip(CircleShape)
                        .background(option.argb())
                        .border(if (on) 2.dp else 1.dp, ring, CircleShape),
                    Alignment.Center,
                ) {
                    if (on) Text("\u2713", color = tick, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
