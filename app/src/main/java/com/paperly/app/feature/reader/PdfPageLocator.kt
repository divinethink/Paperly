package com.paperly.app.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

/** One shown page in list layout px: its [index], [offset] from the viewport top and [size] (height). */
internal data class PageBox(val index: Int, val offset: Float, val size: Float)

/** A point on [page] in page fractions. */
data class PagePoint(val page: Int, val point: Offset)

/**
 * The shown page under a finger that is [y] px below the top of page [from] (its own layout px, may be negative or
 * beyond its height) and [x] across (page fraction). [boxes] are in page order. A finger above/below every shown page
 * lands on the first/last one, clamped to its edge. Null when [from] is not shown any more.
 */
internal fun locateInPages(boxes: List<PageBox>, from: Int, y: Float, x: Float): PagePoint? {
    val shown = boxes.filter { it.size > 0f }
    val origin = shown.firstOrNull { it.index == from } ?: return null
    val abs = origin.offset + y
    val hit = shown.firstOrNull { abs >= it.offset && abs < it.offset + it.size }
        ?: if (abs < shown.first().offset) shown.first() else shown.last()
    return PagePoint(hit.index, Offset(x.coerceIn(0f, 1f), ((abs - hit.offset) / hit.size).coerceIn(0f, 1f)))
}

/** Viewport-relative y (layout px) of a point [y] px below the top of page [page]; null when that page is not shown. */
internal fun viewportYOf(boxes: List<PageBox>, page: Int, y: Float): Float? =
    boxes.firstOrNull { it.index == page }?.let { it.offset + y }

/**
 * Reads the shown pages from the list; lets a selection drag started on one page find the page under the finger.
 * Item offsets start below the list's top content padding, so that is added: [PageBox.offset] is from the viewport top.
 */
internal class PageLocator(private val listState: LazyListState) {
    private fun boxes(): List<PageBox> {
        val info = listState.layoutInfo
        return info.visibleItemsInfo.map {
            PageBox(it.index, (it.offset + info.beforeContentPadding).toFloat(), it.size.toFloat())
        }
    }

    fun locate(from: Int, y: Float, x: Float): PagePoint? = locateInPages(boxes(), from, y, x)

    fun viewportY(page: Int, y: Float): Float? = viewportYOf(boxes(), page, y)
}

internal val LocalPageLocator = staticCompositionLocalOf<PageLocator?> { null }
