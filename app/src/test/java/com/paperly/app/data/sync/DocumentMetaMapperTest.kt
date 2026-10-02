package com.paperly.app.data.sync

import com.paperly.app.domain.sync.DocumentMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentMetaMapperTest {
    private val full = DocumentMeta(
        documentId = "d1",
        title = "Book",
        type = "pdf",
        sizeBytes = 123L,
        checksum = "abc",
        folderId = "f1",
        tags = listOf("a", "b"),
        isFavorite = true,
        createdAt = 10L,
        updatedAt = 20L,
        deletedAt = 30L,
        schemaVersion = 1,
    )

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(full, documentMetaFromMap("d1", full.toFirestoreMap()))
    }

    @Test
    fun nullOptionalsAreOmittedFromTheMap() {
        val map = full.copy(folderId = null, tags = null, deletedAt = null).toFirestoreMap()
        assertFalse(map.containsKey("folderId"))
        assertFalse(map.containsKey("tags"))
        assertFalse(map.containsKey("deletedAt"))
    }

    @Test
    fun idComesFromTheFirestoreDocumentIdNotThePayload() {
        val map = full.toFirestoreMap() + ("documentId" to "other")
        assertEquals("d1", documentMetaFromMap("d1", map)?.documentId)
    }

    @Test
    fun missingRequiredFieldIsSkipped() {
        assertNull(documentMetaFromMap("d1", full.toFirestoreMap() - "checksum"))
    }

    @Test
    fun wrongTypeIsSkippedNotCrashed() {
        assertNull(documentMetaFromMap("d1", full.toFirestoreMap() + ("sizeBytes" to "big")))
    }

    @Test
    fun intNumbersAreAcceptedAndBadTagsDropped() {
        val map = full.toFirestoreMap() + ("updatedAt" to 5) + ("tags" to listOf("x", 7))
        val meta = documentMetaFromMap("d1", map)
        assertEquals(5L, meta?.updatedAt)
        assertEquals(listOf("x"), meta?.tags)
    }
}
