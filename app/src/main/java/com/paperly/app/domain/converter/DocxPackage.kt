package com.paperly.app.domain.converter

import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import org.xml.sax.helpers.DefaultHandler

/** Input is larger than the safety limits (zip-bomb / absurd size guard). */
class ConversionLimitException(message: String) : IOException(message)

/** Read-only view of a DOCX (ZIP) with size limits. Image parts are streamed out lazily by the EPUB writer. */
class DocxPackage(private val zip: ZipFile) : ImageSource, Closeable {
    val hasBody: Boolean get() = zip.getEntry(BODY_PART) != null

    fun verify() {
        if (zip.size() > MAX_ENTRIES) throw ConversionLimitException("too many parts")
    }

    /** Parses [part] if present; a missing optional part (styles, numbering) is simply skipped. */
    fun parseXml(part: String, handler: DefaultHandler) {
        val entry = zip.getEntry(part) ?: return
        zip.getInputStream(entry).use { readXml(BoundedInputStream(it, MAX_XML_BYTES), handler) }
    }

    override fun canRead(part: String): Boolean {
        val entry = zip.getEntry(part)
        if (entry == null || entry.isDirectory) return false
        return entry.size in 0..MAX_IMAGE_BYTES && imageMime(part) != null
    }

    override fun open(part: String): InputStream =
        BoundedInputStream(zip.getInputStream(zip.getEntry(part)), MAX_IMAGE_BYTES)

    override fun close() = zip.close()

    companion object {
        const val BODY_PART = "word/document.xml"
        const val RELS_PART = "word/_rels/document.xml.rels"
        const val STYLES_PART = "word/styles.xml"
        const val NUMBERING_PART = "word/numbering.xml"
        private const val MAX_ENTRIES = 10_000
        private const val MAX_XML_BYTES = 64L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    }
}

/** EPUB 3 core image types we embed; anything else (emf, wmf, svg, tiff, ...) is skipped and reported. */
internal fun imageMime(part: String): String? = when (part.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    else -> null
}

/** Counts bytes actually read (declared ZIP sizes can lie) and aborts past [limit]. */
private class BoundedInputStream(source: InputStream, private val limit: Long) : FilterInputStream(source) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    private fun add(n: Long) {
        count += n
        if (count > limit) throw ConversionLimitException("part too large")
    }
}
