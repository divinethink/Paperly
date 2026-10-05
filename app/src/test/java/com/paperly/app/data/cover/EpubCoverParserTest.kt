package com.paperly.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubCoverParserTest {
    @Test fun resolvesRelativeToOpfDir() = assertEquals("OEBPS/img/c.jpg", EpubCoverParser.resolve("OEBPS", "img/c.jpg"))
    @Test fun resolvesParentSegments() = assertEquals("img/c.jpg", EpubCoverParser.resolve("OEBPS", "../img/c.jpg"))
    @Test fun rootOpf() = assertEquals("c.png", EpubCoverParser.resolve("", "./c.png"))
}
