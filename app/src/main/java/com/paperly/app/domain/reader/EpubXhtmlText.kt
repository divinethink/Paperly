package com.paperly.app.domain.reader

/** One piece of an XHTML file: markup (tag, comment, ...) or text. [skip] = text that is not book text. */
internal class XhtmlToken(val start: Int, val end: Int, val isText: Boolean, val skip: Boolean, val tag: String?)

private val MARKUP = Regex(
    "<!--.*?-->|<!\\[CDATA\\[.*?]]>|<\\?.*?\\?>|<![^>]*>|<(?:[^>\"']|\"[^\"]*\"|'[^']*')*>",
    RegexOption.DOT_MATCHES_ALL,
)
private val TAG_NAME = Regex("^<(/?)([A-Za-z][A-Za-z0-9_.:-]*)")
private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")
private val SKIPPED = setOf("script", "style", "head")
private val INLINE = setOf(
    "a", "abbr", "b", "bdi", "bdo", "big", "cite", "code", "data", "del", "dfn", "em", "font", "i", "ins", "kbd",
    "mark", "q", "rb", "rp", "ruby", "s", "samp", "small", "span", "strong", "sub", "sup", "time", "tt", "u", "var",
)
private const val NO_RAW = -1
private const val NBSP = '\u00A0'

/** "p" for `<p>`, "/p" for `</p>`, null for comments and the like; the namespace prefix is dropped. */
private fun tagOf(markup: String): String? {
    val match = TAG_NAME.find(markup) ?: return null
    val name = match.groupValues[2].substringAfter(':').lowercase()
    return match.groupValues[1] + name
}

/** Splits [xhtml] into markup and text tokens; text inside script/style/head is flagged as skipped. */
internal fun tokenizeXhtml(xhtml: String): List<XhtmlToken> {
    val out = ArrayList<XhtmlToken>()
    var pos = 0
    var skipUntil: String? = null
    for (m in MARKUP.findAll(xhtml)) {
        if (m.range.first > pos) out.add(XhtmlToken(pos, m.range.first, true, skipUntil != null, null))
        val tag = tagOf(m.value)
        out.add(XhtmlToken(m.range.first, m.range.last + 1, false, false, tag))
        skipUntil = when {
            skipUntil != null -> if (tag == "/$skipUntil") null else skipUntil
            tag in SKIPPED && !m.value.endsWith("/>") -> tag
            else -> null
        }
        pos = m.range.last + 1
    }
    if (pos < xhtml.length) out.add(XhtmlToken(pos, xhtml.length, true, skipUntil != null, null))
    return out
}

/**
 * The book text of one XHTML file as the reader shows it: tags removed, entities decoded, every run of whitespace
 * (and a block boundary) one space. Each character remembers its raw position, so a found range can be wrapped.
 */
internal class XhtmlText(
    val norm: String,
    private val rawStart: IntArray,
    private val rawEnd: IntArray,
    private val token: IntArray,
) {
    /** Raw (start, end) ranges covering norm[from, to), one per text token it touches. */
    fun rawPieces(from: Int, to: Int): List<Pair<Int, Int>> {
        val pieces = ArrayList<Pair<Int, Int>>()
        var current = -1
        var first = 0
        var last = 0
        for (i in from until to) {
            if (rawStart[i] == NO_RAW) continue
            if (token[i] != current) {
                if (current >= 0) pieces.add(first to last)
                current = token[i]
                first = rawStart[i]
            }
            last = rawEnd[i]
        }
        if (current >= 0) pieces.add(first to last)
        return pieces
    }
}

private class TextIndexBuilder {
    val sb = StringBuilder()
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    val tokens = ArrayList<Int>()

    fun add(c: Char, start: Int, end: Int, token: Int) {
        sb.append(c)
        starts.add(start)
        ends.add(end)
        tokens.add(token)
    }

    fun build() = XhtmlText(sb.toString(), starts.toIntArray(), ends.toIntArray(), tokens.toIntArray())
}

private fun decodeEntity(body: String): String = when {
    body.startsWith("#x") || body.startsWith("#X") -> codePointText(body.drop(2).toIntOrNull(16))
    body.startsWith("#") -> codePointText(body.drop(1).toIntOrNull())
    else -> when (body) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> " "
        else -> "\uFFFD" // unknown named entity: never matches book text, so a mark there is simply skipped
    }
}

private fun codePointText(code: Int?): String =
    if (code == null || code !in 0..Character.MAX_CODE_POINT) "\uFFFD" else String(Character.toChars(code))

private fun TextIndexBuilder.addText(xhtml: String, token: XhtmlToken, index: Int, pendingSpace: Boolean) {
    var pending = pendingSpace
    var i = token.start
    while (i < token.end) {
        val entity = ENTITY.matchAt(xhtml, i)?.takeIf { it.range.last < token.end }
        val end = if (entity != null) entity.range.last + 1 else i + 1
        val text = if (entity != null) decodeEntity(entity.groupValues[1]) else xhtml[i].toString()
        for (c in text) {
            val space = c.isWhitespace() || c == NBSP
            val last = sb.lastOrNull()
            if (space && (last == null || last == ' ')) continue
            val gap = pending && last != null && last != ' '
            if (!space && gap) add(' ', NO_RAW, NO_RAW, index)
            add(if (space) ' ' else c, i, end, index)
        }
        pending = false
        i = end
    }
}

/** Builds the searchable text of [xhtml]; a non-inline tag between two texts counts as one space. */
internal fun indexXhtmlText(xhtml: String, tokens: List<XhtmlToken>): XhtmlText {
    val builder = TextIndexBuilder()
    var pending = false
    tokens.forEachIndexed { index, token ->
        when {
            !token.isText -> if (token.tag != null && token.tag.removePrefix("/") !in INLINE) pending = true
            !token.skip -> {
                builder.addText(xhtml, token, index, pending)
                pending = false
            }
        }
    }
    return builder.build()
}
