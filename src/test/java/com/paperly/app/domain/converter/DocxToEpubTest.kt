package com.paperly.app.domain.converter

import java.util.zip.ZipEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocxToEpubTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun docx(body: String, extra: Map<String, Any> = emptyMap()) =
        makeZip(tmp.newFile("t.docx"), mapOf("word/document.xml" to documentXml(body)) + extra)

    private fun success(outcome: ConversionOutcome) = (outcome as ConversionOutcome.Success).report

    private fun assertWellFormed(epub: EpubEntries) {
        epub.names.filter { it.endsWith(".xhtml") || it.endsWith(".opf") || it.endsWith(".xml") }.forEach {
            readXml(epub[it].byteInputStream(), org.xml.sax.helpers.DefaultHandler())
        }
    }

    @Test
    fun splitsChaptersAtHeading1AndBuildsToc() {
        val body = para("One", "Heading1") + para("hello", runProps = "<w:b/>") + para("italic", runProps = "<w:i/>") +
            para("Sub", "Heading2") + para("Two", "Heading1") + para("world")
        val (outcome, bytes) = convert(docx(body))
        val epub = readEpub(bytes)
        assertEquals(2, success(outcome).chapters)
        assertEquals("mimetype", epub.names.first())
        assertEquals(ZipEntry.STORED, epub.firstMethod)
        assertEquals("application/epub+zip", epub["mimetype"])
        assertTrue(epub["OEBPS/ch-001.xhtml"].contains("<strong>hello</strong>"))
        assertTrue(epub["OEBPS/ch-001.xhtml"].contains("<em>italic</em>"))
        assertTrue(epub["OEBPS/nav.xhtml"].contains(">Two</a>"))
        assertTrue(epub["OEBPS/nav.xhtml"].contains(">Sub</a>"))
        assertEquals(2, Regex("<itemref ").findAll(epub["OEBPS/content.opf"]).count())
        assertWellFormed(epub)
    }

    @Test
    fun customStyleNamedHeadingIsAHeading() {
        val styles = "<w:styles $STYLES_NS><w:style w:styleId=\"Kicker\"><w:name w:val=\"heading 2\"/></w:style></w:styles>"
        val (_, bytes) = convert(docx(para("Top", "Kicker") + para("body"), mapOf("word/styles.xml" to styles)))
        assertTrue(readEpub(bytes)["OEBPS/ch-001.xhtml"].contains("<h2 id=\"h1\">Top</h2>"))
    }

    @Test
    fun orderedAndBulletedLists() {
        val body = listPara("A", 1) + listPara("B", 1) + listPara("X", 2)
        val (_, bytes) = convert(docx(body, mapOf("word/numbering.xml" to numberingXml())))
        assertTrue(readEpub(bytes)["OEBPS/ch-001.xhtml"].contains("<ul><li>A</li><li>B</li></ul><ol><li>X</li></ol>"))
    }

    @Test
    fun nestedListStaysValid() {
        val body = listPara("A", 1) + listPara("A1", 1, 1) + listPara("B", 1)
        val (_, bytes) = convert(docx(body, mapOf("word/numbering.xml" to numberingXml())))
        assertTrue(readEpub(bytes)["OEBPS/ch-001.xhtml"].contains("<ul><li>A<ul><li>A1</li></ul></li><li>B</li></ul>"))
    }

    @Test
    fun embedsReadableImagesAndReportsSkipped() {
        val body = imagePara("rId1", "a cat") + imagePara("rId2", "gone") + imagePara("rId3", "vector")
        val extra = mapOf(
            "word/_rels/document.xml.rels" to relsXml("rId1" to "media/image1.png", "rId2" to "media/none.png", "rId3" to "media/v.emf"),
            "word/media/image1.png" to byteArrayOf(1, 2, 3),
            "word/media/v.emf" to byteArrayOf(9),
        )
        val (outcome, bytes) = convert(docx(body, extra))
        val epub = readEpub(bytes)
        val report = success(outcome)
        assertEquals(1, report.images)
        assertEquals(2, report.imagesSkipped)
        assertTrue(epub.has("OEBPS/images/img-001.png"))
        assertTrue(epub["OEBPS/ch-001.xhtml"].contains("<img src=\"images/img-001.png\" alt=\"a cat\"/>"))
        assertTrue(epub["OEBPS/content.opf"].contains("media-type=\"image/png\""))
        assertWellFormed(epub)
    }

    @Test
    fun bengaliTextSetsLanguage() {
        val (_, bytes) = convert(docx(para("বাংলা ভাষায় লেখা একটি অনুচ্ছেদ", "Heading1")))
        val epub = readEpub(bytes)
        assertTrue(epub["OEBPS/content.opf"].contains("<dc:language>bn</dc:language>"))
        assertTrue(epub["OEBPS/ch-001.xhtml"].contains("বাংলা ভাষায়"))
        assertWellFormed(epub)
    }

    @Test
    fun noHeadingsGivesOneChapterTitledByFallback() {
        val (outcome, bytes) = convert(docx(para("just text")), title = "My Book")
        val epub = readEpub(bytes)
        assertEquals(1, success(outcome).chapters)
        assertTrue(epub["OEBPS/content.opf"].contains("<dc:title>My Book</dc:title>"))
        assertTrue(epub["OEBPS/nav.xhtml"].contains(">My Book</a>"))
    }

    @Test
    fun rejectsNonDocxAndEmptyDocuments() {
        val text = tmp.newFile("a.docx").apply { writeText("not a zip") }
        assertEquals(ConversionOutcome.Failure(ConversionError.NOT_A_DOCX), convert(text).first)
        val noBody = makeZip(tmp.newFile("b.docx"), mapOf("hello.txt" to "x"))
        assertEquals(ConversionOutcome.Failure(ConversionError.NOT_A_DOCX), convert(noBody).first)
        val (outcome, bytes) = convert(makeZip(tmp.newFile("c.docx"), mapOf("word/document.xml" to documentXml(""))))
        assertEquals(ConversionOutcome.Failure(ConversionError.EMPTY), outcome)
        assertEquals(0, bytes.size)
    }

    @Test
    fun flagsTablesFootnotesAndSkipsTextBoxes() {
        val box = "<mc:AlternateContent><mc:Choice><w:p><w:r><w:txbxContent><w:p><w:r><w:t>boxed</w:t></w:r></w:p>" +
            "</w:txbxContent></w:r></w:p></mc:Choice><mc:Fallback><w:p><w:r><w:t>dupe</w:t></w:r></w:p></mc:Fallback>" +
            "</mc:AlternateContent>"
        val foot = "<w:p><w:r><w:t>see</w:t></w:r><w:r><w:footnoteReference w:id=\"1\"/></w:r></w:p>"
        val table = "<w:tbl><w:tr><w:tc>${para("cell")}</w:tc></w:tr></w:tbl>"
        val (outcome, bytes) = convert(docx(table + foot + box))
        val flags = success(outcome).flags
        val xhtml = readEpub(bytes)["OEBPS/ch-001.xhtml"]
        assertTrue(flags.tablesFlattened && flags.footnotesSkipped && flags.textBoxesSkipped)
        assertTrue(xhtml.contains("<p>cell</p>"))
        assertFalse(xhtml.contains("boxed") || xhtml.contains("dupe"))
    }

    @Test
    fun refusesDoctypeWithExternalEntity() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
            "<w:document $STYLES_NS><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>"
        val (outcome, bytes) = convert(makeZip(tmp.newFile("e.docx"), mapOf("word/document.xml" to evil)))
        assertEquals(ConversionOutcome.Failure(ConversionError.UNREADABLE), outcome)
        assertEquals(0, bytes.size)
    }

    @Test
    fun escapesMarkupAndDropsForbiddenCharacters() {
        assertEquals("a &lt; b &amp; &quot;c&quot;", xmlEscape("a < b & \"c\""))
        assertEquals("ok", xmlEscape("o\u0001k\uD800"))
        assertEquals("\uD83D\uDE00", xmlEscape("\uD83D\uDE00"))
    }

    @Test
    fun resolvesRelationshipTargets() {
        assertEquals("word/media/a.png", resolvePart("media/a.png"))
        assertEquals("word/media/a.png", resolvePart("../word/media/a.png"))
        assertEquals("word/media/a.png", resolvePart("/word/media/a.png"))
    }

    @Test
    fun longDocumentsAreSplitIntoSeveralFiles() {
        val body = (1..450).joinToString("") { para("p$it") }
        val (_, bytes) = convert(docx(body))
        val epub = readEpub(bytes)
        assertTrue(epub.has("OEBPS/ch-002.xhtml"))
        assertEquals(1, Regex("<li><a ").findAll(epub["OEBPS/nav.xhtml"]).count())
    }

    private companion object {
        const val STYLES_NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
    }
}
