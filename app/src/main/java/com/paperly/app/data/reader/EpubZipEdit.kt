package com.paperly.app.data.reader

import com.paperly.app.domain.reader.EpubXhtmlMarks
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val CHAPTER_EXTENSIONS = setOf("xhtml", "html", "htm")
private val ENCODING = Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private const val BOM_FIRST = 0xFE
private const val BOM_SECOND = 0xFF
private const val BYTE_MASK = 0xFF

/** True for the chapter files of an EPUB (the only entries that can hold book text). */
internal fun isChapter(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in CHAPTER_EXTENSIONS

/** The chapter as UTF-8 text, or null when it uses another encoding (then it is left alone). */
internal fun utf8Chapter(bytes: ByteArray): String? {
    val utf16 = bytes.size > 1 && (bytes[0].toInt() and BYTE_MASK).let { it == BOM_FIRST || it == BOM_SECOND }
    if (utf16) return null
    val text = String(bytes, Charsets.UTF_8)
    val declared = ENCODING.find(text)?.groupValues?.get(1)
    return text.takeIf { declared == null || declared.equals("utf-8", ignoreCase = true) }
}

/** Copies [source] to [target] entry by entry; entries named in [changed] get the new bytes. Order and method kept. */
internal fun rewriteEpub(source: File, target: File, changed: Map<String, ByteArray>) {
    ZipFile(source).use { zip ->
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            for (entry in zip.entries()) copyEntry(zip, entry, out, changed[entry.name])
        }
    }
}

private fun copyEntry(zip: ZipFile, entry: ZipEntry, out: ZipOutputStream, replacement: ByteArray?) {
    val next = ZipEntry(entry.name).apply { time = entry.time }
    when {
        entry.isDirectory -> out.putNextEntry(next)
        entry.method == ZipEntry.STORED -> {
            val data = replacement ?: zip.getInputStream(entry).use { it.readBytes() }
            next.method = ZipEntry.STORED
            next.size = data.size.toLong()
            next.compressedSize = data.size.toLong()
            next.crc = CRC32().apply { update(data) }.value
            out.putNextEntry(next)
            out.write(data)
        }
        else -> {
            out.putNextEntry(next)
            if (replacement != null) out.write(replacement) else zip.getInputStream(entry).use { it.copyTo(out) }
        }
    }
    out.closeEntry()
}

/**
 * The new EPUB is a faithful copy: same entries in the same order and methods, untouched entries byte-identical
 * (CRC), and every changed chapter equals the old one once our marks are taken out.
 */
internal fun sameBookAsBefore(old: File, new: File, changed: Set<String>): Boolean = runCatching {
    ZipFile(old).use { a ->
        ZipFile(new).use { b ->
            val before = a.entries().toList()
            val after = b.entries().toList()
            before.size == after.size && before.indices.all { sameEntry(a, before[it], b, after[it], changed) }
        }
    }
}.getOrDefault(false)

private fun sameEntry(a: ZipFile, x: ZipEntry, b: ZipFile, y: ZipEntry, changed: Set<String>): Boolean {
    if (x.name != y.name || x.method != y.method || x.isDirectory != y.isDirectory) return false
    if (x.name !in changed) return x.crc == y.crc && x.size == y.size
    val oldText = utf8Chapter(a.getInputStream(x).use { it.readBytes() })
    val newText = utf8Chapter(b.getInputStream(y).use { it.readBytes() })
    return oldText != null && newText != null && EpubXhtmlMarks.strip(oldText) == EpubXhtmlMarks.strip(newText)
}
