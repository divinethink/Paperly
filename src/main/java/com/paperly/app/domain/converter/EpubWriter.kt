package com.paperly.app.domain.converter

import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class EpubMeta(val title: String, val language: String, val identifier: String, val modifiedUtc: String)

data class EpubStats(val chapters: Int, val images: Int, val imagesSkipped: Int)

private const val CONTENT_DIR = "OEBPS/"
private const val MAX_TOTAL_IMAGE_BYTES = 150L * 1024 * 1024
private const val PAD = 3

private const val CONTAINER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
    "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>" +
    "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>"

private const val STYLE_CSS = "body{line-height:1.5}\np{margin:0 0 .8em}\n" +
    "h1,h2,h3,h4,h5,h6{line-height:1.25}\nimg{max-width:100%;height:auto}\n"

private fun titleOf(meta: EpubMeta) = meta.title.ifBlank { "Untitled" }

private fun chapterName(n: Int) = "ch-" + n.toString().padStart(PAD, '0') + ".xhtml"

private fun imageName(n: Int, part: String) = "images/img-" + n.toString().padStart(PAD, '0') + "." +
    part.substringAfterLast('.').lowercase()

private fun referencedImageParts(blocks: List<Block>): List<String> =
    blocks.flatMap { it.inlines }.filterIsInstance<Inline.Image>().map { it.part }.distinct()

/** Writes a valid EPUB 3 package: mimetype first (stored), container, OPF, nav, CSS, chapters, images. */
internal object EpubWriter {
    fun write(doc: DocxDocument, meta: EpubMeta, images: ImageSource, out: OutputStream): EpubStats {
        val referenced = referencedImageParts(doc.blocks)
        val kept = referenced.filter { images.canRead(it) }
        val names = LinkedHashMap<String, String>()
        kept.forEachIndexed { i, part -> names[part] = imageName(i + 1, part) }
        val files = EpubChapters.split(doc.blocks, meta.title)
        val renderer = XhtmlRenderer(names, meta.language)
        val zip = ZipOutputStream(out)
        putMimetype(zip)
        putText(zip, "META-INF/container.xml", CONTAINER_XML)
        putText(zip, CONTENT_DIR + "style.css", STYLE_CSS)
        val toc = ArrayList<TocEntry>()
        files.forEachIndexed { i, file ->
            val rendered = renderer.render(file, chapterName(i + 1))
            toc += rendered.toc
            putText(zip, CONTENT_DIR + chapterName(i + 1), rendered.xhtml)
        }
        copyImages(zip, images, names)
        putText(zip, CONTENT_DIR + "nav.xhtml", navXhtml(meta, toc))
        putText(zip, CONTENT_DIR + "content.opf", packageOpf(meta, files.size, names.values.toList()))
        zip.finish()
        return EpubStats(files.size, names.size, referenced.size - kept.size)
    }

    private fun putText(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putMimetype(zip: ZipOutputStream) {
        val bytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype")
        entry.method = ZipEntry.STORED
        entry.size = bytes.size.toLong()
        entry.compressedSize = entry.size
        entry.crc = CRC32().apply { update(bytes) }.value
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun copyImages(zip: ZipOutputStream, images: ImageSource, names: Map<String, String>) {
        var total = 0L
        for ((part, name) in names) {
            zip.putNextEntry(ZipEntry(CONTENT_DIR + name))
            total += images.open(part).use { it.copyTo(zip) }
            zip.closeEntry()
            if (total > MAX_TOTAL_IMAGE_BYTES) throw ConversionLimitException("images too large")
        }
    }

    private fun navXhtml(meta: EpubMeta, toc: List<TocEntry>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
        sb.append("lang=\"").append(meta.language).append("\" xml:lang=\"").append(meta.language).append("\">")
        sb.append("<head><title>").append(xmlEscape(titleOf(meta))).append("</title></head><body>")
        sb.append("<nav epub:type=\"toc\" id=\"toc\"><h1>Contents</h1><ol>")
        var inChapter = false
        var inSub = false
        for (entry in toc) {
            if (entry.level == 1 || !inChapter) {
                if (inSub) sb.append("</ol>")
                if (inChapter) sb.append("</li>")
                inSub = false
                inChapter = true
                sb.append("<li>")
            } else {
                if (!inSub) sb.append("<ol>")
                inSub = true
                sb.append("<li>")
            }
            sb.append("<a href=\"").append(entry.href).append("\">")
            sb.append(xmlEscape(entry.title.ifBlank { "Untitled" })).append("</a>")
            if (entry.level != 1 && inSub) sb.append("</li>")
        }
        if (inSub) sb.append("</ol>")
        if (inChapter) sb.append("</li>")
        sb.append("</ol></nav></body></html>")
        return sb.toString()
    }

    private fun packageOpf(meta: EpubMeta, chapters: Int, imagePaths: List<String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" ")
        sb.append("xml:lang=\"").append(meta.language)
        sb.append("\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        sb.append("<dc:identifier id=\"bookid\">").append(xmlEscape(meta.identifier)).append("</dc:identifier>")
        sb.append("<dc:title>").append(xmlEscape(titleOf(meta))).append("</dc:title>")
        sb.append("<dc:language>").append(meta.language).append("</dc:language>")
        sb.append("<meta property=\"dcterms:modified\">").append(xmlEscape(meta.modifiedUtc))
        sb.append("</meta></metadata><manifest>")
        sb.append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
        sb.append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
        for (n in 1..chapters) {
            sb.append("<item id=\"ch").append(n).append("\" href=\"").append(chapterName(n))
            sb.append("\" media-type=\"application/xhtml+xml\"/>")
        }
        imagePaths.forEachIndexed { i, path ->
            sb.append("<item id=\"img").append(i + 1).append("\" href=\"").append(path)
            sb.append("\" media-type=\"").append(imageMime(path)).append("\"/>")
        }
        sb.append("</manifest><spine>")
        for (n in 1..chapters) sb.append("<itemref idref=\"ch").append(n).append("\"/>")
        sb.append("</spine></package>")
        return sb.toString()
    }
}
