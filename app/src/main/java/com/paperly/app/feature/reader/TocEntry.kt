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

/** File part of a link/locator address (no #fragment, no leading slash) so both sides compare equal. */
internal fun hrefKey(address: String): String = address.substringBefore('#').trimStart('/')

internal fun TocEntry.key(): String = hrefKey(link.url().toString())

/** Leading top-level entries with these words are front matter (shown "—", not numbered). Heuristic: no metadata. */
private val FRONT_MATTER_WORDS = listOf(
    "contents", "dedication", "preface", "foreword", "copyright", "cover", "title page", "acknowledg", "epigraph",
    "সূচী", "সূচি", "উৎসর্গ", "ভূমিকা", "মুখবন্ধ", "পূর্বকথা", "কৃতজ্ঞতা",
    "দুটি কথা", "দু'টি কথা", "দুটো কথা",
)

/** Chapter number per entry: null for front matter and for nested entries; top-level chapters count 1, 2, 3 … */
internal fun chapterNumbers(entries: List<TocEntry>): List<Int?> {
    var leading = true
    var next = 1
    return entries.map { entry ->
        if (entry.depth > 0) return@map null
        leading = leading && FRONT_MATTER_WORDS.any { entry.title.contains(it, ignoreCase = true) }
        if (leading) null else next++
    }
}

/**
 * Entry the reader is in: the first entry whose file is [fileKey]; if the file has no entry, the last entry
 * whose file is at or before it in [order] (reading order). -1 when nothing fits.
 */
internal fun currentTocIndex(entries: List<TocEntry>, order: List<String>, fileKey: String?): Int {
    val exact = entries.indexOfFirst { it.key() == fileKey }
    if (exact >= 0 || fileKey == null) return exact
    val at = order.indexOf(fileKey)
    return if (at < 0) -1 else entries.indexOfLast { order.indexOf(it.key()) in 0..at }
}

/** Indices of the parents of [index] (each nearest earlier entry that is one level up), so they can start open. */
internal fun tocAncestors(entries: List<TocEntry>, index: Int): Set<Int> {
    if (index < 0) return emptySet()
    val out = HashSet<Int>()
    var depth = entries[index].depth
    for (i in index - 1 downTo 0) {
        if (entries[i].depth < depth) {
            out += i
            depth = entries[i].depth
        }
    }
    return out
}
