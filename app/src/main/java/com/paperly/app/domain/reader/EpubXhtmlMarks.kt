package com.paperly.app.domain.reader

/** One mark to put into a chapter: where (selected text with a little context) and how it looks. */
data class XhtmlMark(
    val id: String,
    val before: String,
    val highlight: String,
    val after: String,
    val style: String,
    val title: String?,
)

private const val ATTRIBUTE = "data-paperly-id"
private const val CLOSE = "</span>"
private val ID_IN_FILE = Regex("$ATTRIBUTE=\"([A-Za-z0-9_-]{1,64})\"")
private val SPAN_EDGE = Regex("<span\\b[^>]*>|</span\\s*>")
private val ID_OK = Regex("[A-Za-z0-9_-]{1,64}")

/**
 * Marks written into book text as `<span data-paperly-id=...>`: any EPUB reader draws them, nothing else in the
 * file changes. Idempotent by id. A mark whose text cannot be found exactly once-for-sure is skipped, never guessed.
 */
object EpubXhtmlMarks {

    /** Ids of our marks already inside [xhtml]. */
    fun idsIn(xhtml: String): Set<String> = ID_IN_FILE.findAll(xhtml).map { it.groupValues[1] }.toSet()

    /** Adds the marks whose id is not in the file yet; returns [xhtml] itself when nothing was added. */
    fun apply(xhtml: String, marks: List<XhtmlMark>): String {
        val present = idsIn(xhtml)
        return marks.filter { it.id !in present && ID_OK.matches(it.id) }.fold(xhtml, ::applyOne)
    }

    /** Takes the marks with these ids out again (the text stays); [xhtml] itself when none is found. */
    fun remove(xhtml: String, ids: Set<String>): String =
        ids.fold(xhtml) { text, id -> generateSequence(text) { unwrapOne(it, id) }.last() }

    /** The file as it was before any of our marks. */
    fun strip(xhtml: String): String = remove(xhtml, idsIn(xhtml))

    private fun applyOne(xhtml: String, mark: XhtmlMark): String {
        val text = indexXhtmlText(xhtml, tokenizeXhtml(xhtml))
        val from = findMark(text.norm, mark)
        if (from < 0) return xhtml
        val pieces = text.rawPieces(from, from + collapse(mark.highlight).length)
        if (pieces.isEmpty()) return xhtml
        val out = StringBuilder(xhtml)
        for ((start, end) in pieces.asReversed()) {
            out.insert(end, CLOSE)
            out.insert(start, openTag(mark))
        }
        return out.toString()
    }

    private fun openTag(mark: XhtmlMark): String {
        val title = mark.title?.takeIf { it.isNotBlank() }?.let { " title=\"${escape(it)}\"" }.orEmpty()
        return "<span class=\"paperly-mark\" $ATTRIBUTE=\"${mark.id}\" style=\"${escape(mark.style)}\"$title>"
    }

    /** Removes the first span of [id] (start tag and its matching end tag); null when there is none. */
    private fun unwrapOne(xhtml: String, id: String): String? {
        val start = Regex("<span\\b[^>]*\\b$ATTRIBUTE=\"${Regex.escape(id)}\"[^>]*>").find(xhtml) ?: return null
        var depth = 1
        var close: MatchResult? = null
        var scan = SPAN_EDGE.find(xhtml, start.range.last + 1)
        while (scan != null && close == null) {
            val value = scan.value
            when {
                value.startsWith("</") -> if (--depth == 0) close = scan
                !value.endsWith("/>") -> depth++
            }
            scan = scan.next()
        }
        val end = close ?: return null
        return xhtml.removeRange(end.range).removeRange(start.range)
    }
}

/** Whitespace runs (and no-break spaces) become one space; ends trimmed. */
internal fun collapse(text: String): String =
    text.map { if (it.isWhitespace() || it == '\u00A0') ' ' else it }.joinToString("").replace(Regex(" +"), " ").trim()

/** Index in [norm] where the mark's text starts, or -1 when it is not there or not certain enough. */
internal fun findMark(norm: String, mark: XhtmlMark): Int {
    val target = collapse(mark.highlight)
    if (target.isEmpty()) return -1
    val hits = generateSequence(norm.indexOf(target)) { norm.indexOf(target, it + 1).takeIf { next -> next >= 0 } }
        .takeWhile { it >= 0 }
        .toList()
    val before = collapse(mark.before)
    val after = collapse(mark.after)
    val hasContext = before.isNotEmpty() || after.isNotEmpty()
    val withContext = if (hasContext) {
        hits.firstOrNull { endsLike(norm, it, before) && startsLike(norm, it + target.length, after) }
    } else {
        null
    }
    return withContext ?: hits.singleOrNull() ?: -1
}

private fun endsLike(norm: String, index: Int, context: String): Boolean {
    if (context.isEmpty()) return true
    val tail = norm.substring(maxOf(0, index - context.length - 1), index).trim()
    return tail.endsWith(context) || (index <= context.length && context.endsWith(tail))
}

private fun startsLike(norm: String, index: Int, context: String): Boolean {
    if (context.isEmpty()) return true
    val head = norm.substring(index, minOf(norm.length, index + context.length + 1)).trim()
    return head.startsWith(context) || (norm.length - index <= context.length && context.startsWith(head))
}

private fun escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
