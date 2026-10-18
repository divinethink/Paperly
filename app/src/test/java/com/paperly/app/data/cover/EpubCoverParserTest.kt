package com.paperly.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubCoverParserTest {
    @Test fun resolvesRelativeToOpfDir() = assertEquals("OEBPS/img/c.jpg", EpubCoverParser.resolve("OEBPS", "img/c.jpg"))

    @Test fun resolvesParentSegments() = assertEquals("img/c.jpg", EpubCoverParser.resolve("OEBPS", "../img/c.jpg"))

    @Test fun rootOpf() = assertEquals("c.png", EpubCoverParser.resolve("", "./c.png"))

    @Test fun authorFromCreatorTag() {
        assertEquals("Imran", EpubCoverParser.authorFrom("<dc:creator opf:role=\"aut\">Imran</dc:creator>"))
    }

    @Test fun authorsJoinedAndEntitiesDecoded() {
        val opf = "<dc:creator>A &amp; B</dc:creator><dc:creator>C</dc:creator>"
        assertEquals("A & B, C", EpubCoverParser.authorFrom(opf))
    }

    @Test fun cdataAuthorKept() {
        assertEquals("Name", EpubCoverParser.authorFrom("<dc:creator><![CDATA[Name]]></dc:creator>"))
    }

    @Test fun noAuthorIsNull() {
        assertNull(EpubCoverParser.authorFrom("<dc:title>T</dc:title><dc:creator>  </dc:creator>"))
    }
}
