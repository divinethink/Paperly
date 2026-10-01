package com.paperly.app.feature.reader

import org.readium.r2.shared.publication.Link

/** One flattened table-of-contents row; [link] is what the navigator jumps to. */
internal data class TocEntry(val title: String, val depth: Int, val link: Link)

/** Indices of rows to show: a collapsed entry (index not in [expanded]) hides all deeper rows after it. */
internal fun visibleToc(entries: List<TocEntry>, expanded: Set<Int>): List<Int> {
    val out = ArrayList<Int>()
    var hideDeeperThan = Int.MAX_VALUE
    entries.forEachIndexed { index, entry ->
        if (entry.depth > hideDeeperThan) return@forEachIndexed
        hideDeeperThan = if (index in expanded) Int.MAX_VALUE else entry.depth
        out += index
    }
    return out
}

/** Flattens nested TOC links depth-first. Untitled links are skipped but their children are kept. */
internal fun flattenToc(links: List<Link>, depth: Int = 0): List<TocEntry> = links.flatMap { link ->
    val title = link.title?.trim().orEmpty()
    if (title.isEmpty()) {
        flattenToc(link.children, depth)
    } else {
        listOf(TocEntry(title, depth, link)) + flattenToc(link.children, depth + 1)
    }
}
