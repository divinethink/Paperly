package com.paperly.app.domain.converter

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" " +
    "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\""

internal fun documentXml(body: String) =
    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $NS><w:body>$body</w:body></w:document>"

internal fun para(text: String, style: String? = null, runProps: String = ""): String {
    val pPr = if (style != null) "<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>" else ""
    return "<w:p>$pPr<w:r><w:rPr>$runProps</w:rPr><w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"
}

internal fun listPara(text: String, numId: Int, level: Int = 0) =
    "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$numId\"/></w:numPr></w:pPr>" +
        "<w:r><w:t>$text</w:t></w:r></w:p>"

internal fun imagePara(rId: String, alt: String) =
    "<w:p><w:r><w:drawing><wp:inline><wp:docPr id=\"1\" name=\"Picture 1\" descr=\"$alt\"/><a:graphic><a:graphicData>" +
        "<pic:pic><pic:blipFill><a:blip r:embed=\"$rId\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>" +
        "</wp:inline></w:drawing></w:r></w:p>"

internal fun numberingXml() =
    "<?xml version=\"1.0\"?><w:numbering $NS>" +
        "<w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/></w:lvl></w:abstractNum>" +
        "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/></w:lvl></w:abstractNum>" +
        "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>" +
        "<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>"

internal fun relsXml(vararg rels: Pair<String, String>) =
    "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        rels.joinToString("") { "<Relationship Id=\"${it.first}\" Type=\"image\" Target=\"${it.second}\"/>" } +
        "</Relationships>"

/** Writes a DOCX-shaped ZIP: [parts] maps part name -> String or ByteArray content. */
internal fun makeZip(file: File, parts: Map<String, Any>): File {
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, content) in parts) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(if (content is ByteArray) content else content.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return file
}

internal fun convert(docx: File, title: String = "Doc"): Pair<ConversionOutcome, ByteArray> {
    val out = ByteArrayOutputStream()
    val outcome = DocxToEpub.convert(docx, out, ConvertOptions(title, "urn:uuid:test", "2026-10-05T00:00:00Z"))
    return outcome to out.toByteArray()
}

internal class EpubEntries(val names: List<String>, val firstMethod: Int, private val text: Map<String, String>) {
    operator fun get(name: String): String = text.getValue(name)

    fun has(name: String) = name in names
}

internal fun readEpub(bytes: ByteArray): EpubEntries {
    val names = ArrayList<String>()
    val text = HashMap<String, String>()
    var firstMethod = -1
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (names.isEmpty()) firstMethod = entry.method
            names += entry.name
            text[entry.name] = String(zip.readBytes(), Charsets.UTF_8)
        }
    }
    return EpubEntries(names, firstMethod, text)
}
