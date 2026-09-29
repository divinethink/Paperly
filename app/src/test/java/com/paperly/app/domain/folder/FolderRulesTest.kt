package com.paperly.app.domain.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderRulesTest {
    @Test
    fun folderNameIsTrimmedAndCapped() {
        assertEquals("Work", normalizeFolderName("  Work  "))
        assertEquals(MAX_FOLDER_NAME_LENGTH, normalizeFolderName("x".repeat(MAX_FOLDER_NAME_LENGTH + 10))?.length)
    }

    @Test
    fun blankFolderNameIsRejected() {
        assertNull(normalizeFolderName("   "))
        assertNull(normalizeFolderName(""))
    }

    @Test
    fun tagsAreTrimmedDeduplicatedAndBlanksDropped() {
        assertEquals(listOf("Tax", "2026"), parseTags(" Tax , tax,, 2026 ,"))
    }

    @Test
    fun tagsAreCappedInCountAndLength() {
        val many = (1..MAX_TAGS + 5).joinToString(",") { "t$it" }
        assertEquals(MAX_TAGS, parseTags(many).size)
        assertEquals(MAX_TAG_LENGTH, parseTags("y".repeat(MAX_TAG_LENGTH + 5)).single().length)
    }

    @Test
    fun reservedStorageSeparatorIsStripped() {
        assertEquals(listOf("ab"), parseTags("a\u001Fb"))
    }
}
