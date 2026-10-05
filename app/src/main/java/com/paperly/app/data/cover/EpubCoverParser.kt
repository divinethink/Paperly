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
        val container = zip.read("META-INF/container.xml") ?: return null
        val opfPath = rootfile.find(container)?.groupValues?.get(1) ?: return null
        val opf = zip.read(opfPath) ?: return null
        val items = itemTag.findAll(opf).map { it.value }.toList()
        val byProp = items.firstOrNull { attr(it, "properties")?.split(' ')?.contains("cover-image") == true }
        val byMeta = metaCover.find(opf)?.groupValues?.get(1)?.let { id -> items.firstOrNull { attr(it, "id") == id } }
        val href = (byProp ?: byMeta)?.let { attr(it, "href") } ?: return null
        val base = opfPath.substringBeforeLast('/', "")
        return resolve(base, href)
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
        getEntry(name)?.takeIf { it.size in 0..MAX_XML }?.let { getInputStream(it).use { s -> s.readBytes().toString(Charsets.UTF_8) } }

    private const val MAX_XML = 2L * 1024 * 1024
}
