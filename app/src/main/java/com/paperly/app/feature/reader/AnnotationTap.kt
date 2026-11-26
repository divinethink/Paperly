package com.paperly.app.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.paperly.app.domain.reader.Annotation

/** The top-most saved mark under [pos] (pixels inside a page of [size]); null when the tap hit none. */
internal fun hitMark(annotations: List<Annotation>, pos: Offset, size: IntSize): Annotation? {
    val fx = pos.x / size.width
    val fy = pos.y / size.height
    return annotations.lastOrNull { a ->
        a.rects.any { fx in it.left..it.right && fy in it.top..it.bottom }
    }
}

/** A tap on no mark: drop the quick box and any picked mark or selection; if nothing was open, toggle the bars. */
internal fun handleEmptyTap(edit: PendingEdit, annotate: Boolean, select: SelectEdit?) {
    val blank = !annotate && select?.hooks?.selection == null && edit.rects.isEmpty()
    select?.hooks?.onClear?.invoke()
    edit.cancelQuick(annotate) // a tap beside the quick box drops it
    if (edit.pending.editing) edit.pending.onDeselect() // ...and lets go of a picked mark
    if (blank) edit.pending.onBlankTap() // nothing was open: toggle the bars
}
