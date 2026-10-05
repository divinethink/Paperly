package com.paperly.app.data.cover

import java.util.zip.ZipFile

/** Pure EPUB cover-path lookup (container.xml -> OPF -> cover item). Returns the zip entry path or null. */
internal object EpubCoverParser {
    private val rootfile = Regex("""<rootfile[^>]*full-path\s*=\s*["']([^"']+)["']""")
    private val itemTag = Regex("""<item\b[^>]*>""")
    private val metaCover = Regex("""<meta\b[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""")

    private fun attr(tag: String, name: String) =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(tag)?.groupValues?.get(1)

    fun coverPath(zip: ZipFile): String? {
        val container = zip.read("META-INF/container.xml")
        val opfPath = container?.let { rootfile.find(it)?.groupValues?.get(1) }
        val opf = opfPath?.let { zip.read(it) }
        val href = opf?.let { coverHref(it) }
        return if (opfPath != null && href != null) resolve(opfPath.substringBeforeLast('/', ""), href) else null
    }

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
}
