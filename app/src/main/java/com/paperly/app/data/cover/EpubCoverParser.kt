package com.paperly.app.data.cover

import java.util.zip.ZipFile

/** Pure EPUB cover-path lookup (container.xml -> OPF -> cover item). Returns the zip entry path or null. */
internal object EpubCoverParser {
    private val rootfile = Regex("""<rootfile[^>]*full-path\s*=\s*["']([^"']+)["']""")
    private val itemTag = Regex("""<item\b[^>]*>""")
    private val metaCover = Regex("""<meta\b[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""")

    private val creatorTag =
        Regex("""<(?:\w+:)?creator\b[^>]*>(.*?)</(?:\w+:)?creator>""", RegexOption.DOT_MATCHES_ALL)
    private val anyTag = Regex("""<[^>]*>""")
    private val spaces = Regex("""\s+""")

    private fun attr(tag: String, name: String) =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

    fun coverPath(zip: ZipFile): String? {
        val container = zip.read("META-INF/container.xml")
        val opfPath = container?.let { rootfile.find(it)?.groupValues?.get(1) }
        val opf = opfPath?.let { zip.read(it) }
        val href = opf?.let { coverHref(it) }
        return if (opfPath != null && href != null) resolve(opfPath.substringBeforeLast('/', ""), href) else null
    }

    /** dc:creator names from the OPF ("A, B"), or null. Display only; the file is never modified. */
    fun author(zip: ZipFile): String? {
        val opfPath = zip.read("META-INF/container.xml")?.let { rootfile.find(it)?.groupValues?.get(1) }
        return opfPath?.let { zip.read(it) }?.let { authorFrom(it) }
    }

    internal fun authorFrom(opf: String): String? =
        creatorTag.findAll(opf).map { clean(it.groupValues[1]) }.filter { it.isNotEmpty() }
            .take(MAX_AUTHORS).joinToString(", ").take(MAX_AUTHOR_CHARS).ifEmpty { null }

    private fun clean(raw: String): String =
        anyTag.replace(raw.replace("<![CDATA[", "").replace("]]>", ""), " ")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&").replace(spaces, " ").trim()

    private fun coverHref(opf: String): String? {
        val items = itemTag.findAll(opf).map { it.value }.toList()
        val byProp = items.firstOrNull { attr(it, "properties")?.split(' ')?.contains("cover-image") == true }
        val byMeta = metaCover.find(opf)?.groupValues?.get(1)?.let { id -> items.firstOrNull { attr(it, "id") == id } }
        return (byProp ?: byMeta)?.let { attr(it, "href") }
    }

    internal fun resolve(base: String, href: String): String {
        val parts = ArrayDeque<String>()
        if (base.isNotEmpty()) parts.addAll(base.split('/'))
        for (seg in href.substringBefore('#').split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    private fun ZipFile.read(name: String): String? =
        getEntry(name)?.takeIf { it.size in 0..MAX_XML }?.let { e ->
            getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private const val MAX_XML = 2L * 1024 * 1024
    private const val MAX_AUTHORS = 3
    private const val MAX_AUTHOR_CHARS = 80
}
