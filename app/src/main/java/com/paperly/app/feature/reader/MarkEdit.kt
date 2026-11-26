package com.paperly.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.MatchRect

private const val MIN_SPAN = 1e-4f // a frame thinner than this (page fraction) cannot be stretched

/**
 * A saved mark picked for editing (tap on it). [rects] are its parts as stored, [frame] the box round them; the box
 * with its handles is the single pending part of the page, so a drag changes only the pending box until it is lifted.
 */
data class EditingMark(val id: String, val page: Int, val rects: List<MatchRect>, val frame: MatchRect)

/** Maps [rects] from inside [from] to the same relative place inside [to] (move and resize), kept on the page. */
internal fun remapRects(rects: List<MatchRect>, from: MatchRect, to: MatchRect): List<MatchRect> {
    val fromW = from.right - from.left
    val fromH = from.bottom - from.top
    val scaleX = if (fromW < MIN_SPAN) 0f else (to.right - to.left) / fromW
    val scaleY = if (fromH < MIN_SPAN) 0f else (to.bottom - to.top) / fromH
    fun x(v: Float) = (to.left + (v - from.left) * scaleX).coerceIn(0f, 1f)
    fun y(v: Float) = (to.top + (v - from.top) * scaleY).coerceIn(0f, 1f)
    return rects.map { MatchRect(x(it.left), y(it.top), x(it.right), y(it.bottom)) }
}

/** [items] with the picked mark drawn at the box being dragged ([frame]), so the mark follows the handles live. */
internal fun previewMarks(items: List<Annotation>, editing: EditingMark?, frame: MatchRect?): List<Annotation> {
    if (editing == null) return items
    val shown = remapRects(editing.rects, editing.frame, frame ?: editing.frame)
    return items.map { if (it.id == editing.id) it.copy(rects = shown) else it }
}

/** The marks the pages draw: the saved ones, the picked one following its box. */
@Composable
internal fun rememberShownMarks(
    controller: AnnotationController,
    items: List<Annotation>,
    pending: PendingParts?,
): List<Annotation> {
    val editing by controller.editing.collectAsStateWithLifecycle()
    val frame = pending?.takeIf { it.editing }?.rects?.singleOrNull()
    return remember(items, editing, frame) { previewMarks(items, editing, frame) }
}
