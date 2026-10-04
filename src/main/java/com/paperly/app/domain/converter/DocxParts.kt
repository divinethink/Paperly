package com.paperly.app.domain.converter

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

private const val MAX_HEADING = 6

/** `word/_rels/document.xml.rels`: relationship id -> normalised package part (internal targets only). */
internal class RelsHandler : DefaultHandler() {
    val targets = HashMap<String, String>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
        if (localName != "Relationship" || attrs.attr("TargetMode") == "External") return
        val id = attrs.attr("Id")
        val target = attrs.attr("Target")
        if (id != null && target != null) targets[id] = resolvePart(target)
    }
}

/** Targets are relative to `word/`; `/x` is package-absolute; `..` is resolved. */
internal fun resolvePart(target: String): String {
    val segments = ArrayDeque<String>()
    if (!target.startsWith("/")) segments.add("word")
    target.trimStart('/').split('/').forEach { s ->
        when (s) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments.add(s)
        }
    }
    return segments.joinToString("/")
}

/** `word/styles.xml`: style id -> heading level, from the style's display name ("heading 2", "Title"). */
internal class StylesHandler : DefaultHandler() {
    val headingLevels = HashMap<String, Int>()
    private var styleId: String? = null

    override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
        when (localName) {
            "style" -> styleId = attrs.attr("styleId")
            "name" -> {
                val level = headingLevelOfName(attrs.attr("val"))
                val id = styleId
                if (id != null && level != null) headingLevels[id] = level
            }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (localName == "style") styleId = null
    }
}

internal fun headingLevelOfName(name: String?): Int? {
    val n = name?.trim()?.lowercase() ?: return null
    return when {
        n == "title" -> 1
        n.startsWith("heading ") -> n.removePrefix("heading ").trim().toIntOrNull()?.coerceIn(1, MAX_HEADING)
        else -> null
    }
}

/** `word/numbering.xml`: is list `numId` at level `ilvl` numbered (anything but a bullet) or bulleted. */
internal class NumberingHandler : DefaultHandler() {
    private val abstractFormats = HashMap<Pair<String, Int>, String>()
    private val numToAbstract = HashMap<String, String>()
    private var abstractId: String? = null
    private var numId: String? = null
    private var level = 0

    override fun startElement(uri: String?, localName: String?, qName: String?, attrs: Attributes) {
        when (localName) {
            "abstractNum" -> abstractId = attrs.attr("abstractNumId")
            "lvl" -> level = attrs.attr("ilvl")?.toIntOrNull() ?: 0
            "numFmt" -> abstractId?.let { abstractFormats[it to level] = attrs.attr("val").orEmpty() }
            "num" -> numId = attrs.attr("numId")
            "abstractNumId" -> numId?.let { n -> attrs.attr("val")?.let { numToAbstract[n] = it } }
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localName) {
            "abstractNum" -> abstractId = null
            "num" -> numId = null
        }
    }

    fun isOrdered(numId: String, ilvl: Int): Boolean {
        val abstract = numToAbstract[numId] ?: return false
        val format = abstractFormats[abstract to ilvl] ?: abstractFormats[abstract to 0] ?: return false
        return format != "bullet" && format != "none"
    }
}
