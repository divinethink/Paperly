package com.paperly.app.domain.converter

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

private const val MAX_HEADING = 6
private val HEADING_ID = Regex("(?i)^heading([1-6])$")

// Content we cannot represent: tracked move-sources and text boxes (their text is skipped and reported).
// `Fallback` is the legacy duplicate of an AlternateContent drawing.
private val SKIPPED = setOf("moveFrom", "txbxContent")

/**
 * SAX handler for `word/document.xml`: paragraphs -> [Block]s with bold/italic/underline runs and inline images.
 * Only `w:t` text is collected; tables are flattened; footnotes and text boxes are skipped and flagged.
 */
internal class DocxBodyHandler(
    private val headings: Map<String, Int>,
    private val numbering: NumberingHandler,
    private val rels: Map<String, String>,
) : DefaultHandler() {
    private val blocks = ArrayList<Block>()
    private val inlines = ArrayList<Inline>()
    private val text = StringBuilder()
    private var depth = 0
    private var skipUntil = -1
    private var inText = false
    private var tables = false
    private var footnotes = false
    private var textBoxes = false
    private var style: String? = null
    private var numId: String? = null
    private var ilvl = 0
    private var outline: Int? = null
    private var altText = ""
    private var bold = false
    private var italic = false
    private var underline = false

    override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
        depth++
        if (skipUntil >= 0) return
        val name = localName.orEmpty()
        val word = uri.orEmpty() in WORD_NAMESPACES
        if (name == "Fallback" || (word && name in SKIPPED)) {
            skipUntil = depth
            if (name == "txbxContent") textBoxes = true
            return
        }
        if (word) startWord(name, attrs) else startOther(name, attrs)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (skipUntil >= 0) {
            if (depth == skipUntil) skipUntil = -1
        } else if (uri.orEmpty() in WORD_NAMESPACES) {
            when (localName) {
                "t" -> {
                    inText = false
                    addText(text.toString())
                    text.setLength(0)
                }
                "p" -> endParagraph()
            }
        }
        depth--
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inText && skipUntil < 0) text.append(ch, start, length)
    }

    private fun startWord(name: String, attrs: Attributes) {
        when (name) {
            "p" -> beginParagraph()
            "pStyle" -> style = attrs.attr("val")
            "numId" -> numId = attrs.attr("val")
            "ilvl" -> ilvl = attrs.attr("val")?.toIntOrNull() ?: 0
            "outlineLvl" -> outline = attrs.attr("val")?.toIntOrNull()
            "tbl" -> tables = true
            "footnoteReference", "endnoteReference" -> footnotes = true
            else -> startRun(name, attrs)
        }
    }

    private fun startRun(name: String, attrs: Attributes) {
        when (name) {
            "r" -> {
                bold = false
                italic = false
                underline = false
            }
            "b" -> bold = isOn(attrs)
            "i" -> italic = isOn(attrs)
            "u" -> underline = attrs.attr("val") != "none"
            "t" -> {
                inText = true
                text.setLength(0)
            }
            "br" -> if (attrs.attr("type").let { it == null || it == "textWrapping" }) inlines += Inline.Break
            "tab" -> addText(" ")
            "noBreakHyphen" -> addText("-")
        }
    }

    private fun startOther(name: String, attrs: Attributes) {
        when (name) {
            "docPr" -> altText = attrs.attr("descr").orEmpty()
            "blip" -> rels[attrs.attr("embed").orEmpty()]?.let { addImage(it) }
            "imagedata" -> rels[attrs.attr("id").orEmpty()]?.let { addImage(it) }
        }
    }

    private fun addImage(part: String) {
        inlines += Inline.Image(part, altText)
        altText = ""
    }

    private fun beginParagraph() {
        inlines.clear()
        style = null
        numId = null
        ilvl = 0
        outline = null
    }

    private fun endParagraph() {
        while (inlines.lastOrNull() is Inline.Break) inlines.removeAt(inlines.lastIndex)
        val hasContent = inlines.any { (it is Inline.Text && it.text.isNotBlank()) || it is Inline.Image }
        if (hasContent) {
            val content = inlines.toList()
            val level = headingLevelOf(style, headings, outline)
            val list = numId?.takeIf { it != "0" }
            blocks += when {
                level != null -> Block.Heading(level, content)
                list != null -> Block.ListItem(numbering.isOrdered(list, ilvl), ilvl, content)
                else -> Block.Paragraph(content)
            }
        }
        inlines.clear()
    }

    private fun addText(value: String) {
        if (value.isEmpty()) return
        val last = inlines.lastOrNull()
        if (last is Inline.Text && last.hasStyle(bold, italic, underline)) {
            inlines[inlines.lastIndex] = last.copy(text = last.text + value)
        } else {
            inlines += Inline.Text(value, bold, italic, underline)
        }
    }

    val result: DocxDocument get() = DocxDocument(blocks.toList(), DocxFlags(tables, footnotes, textBoxes))
}

private fun Inline.Text.hasStyle(b: Boolean, i: Boolean, u: Boolean) = bold == b && italic == i && underline == u

/** `w:b`/`w:i` are on unless `w:val` says otherwise. */
private fun isOn(attrs: Attributes): Boolean = attrs.attr("val") !in setOf("0", "false", "off")

internal fun headingLevelOf(style: String?, byStyle: Map<String, Int>, outline: Int?): Int? {
    val fromStyle = style?.let { byStyle[it] ?: HEADING_ID.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    return fromStyle ?: outline?.takeIf { it in 0 until MAX_HEADING }?.plus(1)
}
