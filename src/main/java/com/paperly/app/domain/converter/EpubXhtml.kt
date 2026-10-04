package com.paperly.app.domain.converter

private const val MAX_TOC_LEVEL = 3
private const val ESCAPE_HEADROOM_DIVISOR = 8
private const val MAX_LIST_LEVEL = 3
private const val MIN_PRINTABLE = 0x20
private const val BMP_LOW_END = 0xD7FF
private const val BMP_HIGH_START = 0xE000
private const val BMP_END = 0xFFFD
private const val SUPPLEMENTARY_START = 0x10000
private const val MAX_CODE_POINT = 0x10FFFF

internal data class TocEntry(val level: Int, val title: String, val href: String)

internal data class RenderedFile(val xhtml: String, val toc: List<TocEntry>)

/** Blocks -> XHTML. [imagePaths] maps package parts to EPUB-relative paths; unmapped images are left out. */
internal class XhtmlRenderer(private val imagePaths: Map<String, String>, private val language: String) {
    private var anchors = 0

    fun render(file: EpubFile, fileName: String): RenderedFile {
        val body = StringBuilder()
        val toc = ArrayList<TocEntry>()
        if (file.inToc) toc += TocEntry(1, file.title, fileName)
        var chapterHeadingPending = file.inToc // the chapter's own heading is already its TOC entry
        val lists = ArrayDeque<String>()
        for (block in file.blocks) {
            if (block !is Block.ListItem) closeLists(lists, body)
            when (block) {
                is Block.Heading -> {
                    anchors++
                    val level = block.level.coerceIn(1, MAX_HEADING_TAG)
                    body.append("<h").append(level).append(" id=\"h").append(anchors).append("\">")
                    appendInlines(block.inlines, body)
                    body.append("</h").append(level).append(">")
                    if (!chapterHeadingPending && level <= MAX_TOC_LEVEL) {
                        toc += TocEntry(2, block.inlines.plainText(), "$fileName#h$anchors")
                    }
                    chapterHeadingPending = false
                }
                is Block.Paragraph -> {
                    body.append("<p>")
                    appendInlines(block.inlines, body)
                    body.append("</p>")
                }
                is Block.ListItem -> openItem(block, lists, body)
            }
        }
        closeLists(lists, body)
        return RenderedFile(page(file.title, body), toc)
    }

    private fun openItem(item: Block.ListItem, lists: ArrayDeque<String>, sb: StringBuilder) {
        val tag = if (item.ordered) "ol" else "ul"
        // Nesting grows one level at a time, so a jump in w:ilvl never yields an invalid list.
        val depth = minOf(item.level.coerceIn(0, MAX_LIST_LEVEL) + 1, lists.size + 1)
        while (lists.size > depth) closeList(lists, sb)
        if (lists.size == depth && lists.last() != tag) closeList(lists, sb)
        if (lists.size == depth) sb.append("</li>")
        if (lists.size < depth) {
            sb.append('<').append(tag).append('>')
            lists.addLast(tag)
        }
        sb.append("<li>")
        appendInlines(item.inlines, sb)
    }

    private fun appendInlines(items: List<Inline>, sb: StringBuilder) {
        for (item in items) {
            when (item) {
                is Inline.Break -> sb.append("<br/>")
                is Inline.Image -> imagePaths[item.part]?.let {
                    sb.append("<img src=\"").append(it).append("\" alt=\"").append(xmlEscape(item.alt)).append("\"/>")
                }
                is Inline.Text -> appendText(item, sb)
            }
        }
    }

    private fun appendText(item: Inline.Text, sb: StringBuilder) {
        val tags = listOfNotNull(
            "strong".takeIf { item.bold },
            "em".takeIf { item.italic },
            "u".takeIf { item.underline },
        )
        tags.forEach { sb.append('<').append(it).append('>') }
        sb.append(xmlEscape(item.text))
        tags.asReversed().forEach { sb.append("</").append(it).append('>') }
    }

    private fun page(title: String, body: CharSequence): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"$language\" xml:lang=\"$language\">" +
            "<head><title>${xmlEscape(title.ifBlank { "Untitled" })}</title>" +
            "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>$body</body></html>"
}

private const val MAX_HEADING_TAG = 6

private fun closeList(lists: ArrayDeque<String>, sb: StringBuilder) {
    sb.append("</li></").append(lists.removeLast()).append('>')
}

private fun closeLists(lists: ArrayDeque<String>, sb: StringBuilder) {
    while (lists.isNotEmpty()) closeList(lists, sb)
}

/** Escapes XML text/attribute content and drops characters XML 1.0 forbids (control chars, lone surrogates). */
internal fun xmlEscape(s: String): String {
    val sb = StringBuilder(s.length + s.length / ESCAPE_HEADROOM_DIVISOR)
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        i += Character.charCount(cp)
        when (cp) {
            '&'.code -> sb.append("&amp;")
            '<'.code -> sb.append("&lt;")
            '>'.code -> sb.append("&gt;")
            '"'.code -> sb.append("&quot;")
            '\''.code -> sb.append("&apos;")
            else -> if (isXmlChar(cp)) sb.appendCodePoint(cp)
        }
    }
    return sb.toString()
}

private fun isXmlChar(cp: Int): Boolean =
    cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
        cp in MIN_PRINTABLE..BMP_LOW_END || cp in BMP_HIGH_START..BMP_END || cp in SUPPLEMENTARY_START..MAX_CODE_POINT
