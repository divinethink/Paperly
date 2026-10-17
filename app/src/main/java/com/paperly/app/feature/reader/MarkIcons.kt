package com.paperly.app.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType

private val MarkButton = 44.dp
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
    onPick: (AnnotationType) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        MARK_TYPES.forEach { type -> MarkIcon(type, color.argb(), type == selected) { onPick(type) } }
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
