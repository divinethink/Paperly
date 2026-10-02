package com.paperly.app.feature.storage

import com.paperly.app.domain.document.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageStateTest {
    private fun doc(id: String, type: String = "pdf", size: Long = 1L) = Document(id, id, type, size, 0L)

    @Test
    fun byTypeSumsAllDocumentsLargestFirst() {
        val docs = listOf(doc("a", "pdf", 10L), doc("b", "epub", 50L), doc("c", "pdf", 20L))
        val state = buildStorageState(null, docs, emptyList())
        assertEquals(listOf("epub", "pdf"), state.byType.map { it.type })
        assertEquals(30L, state.byType.last().bytes)
        assertEquals(2, state.byType.last().count)
    }

    @Test
    fun largestIsCappedAndSorted() {
        val docs = (1..8).map { doc("d$it", size = it.toLong()) }
        val state = buildStorageState(null, docs, emptyList())
        assertEquals(LARGEST_COUNT, state.largest.size)
        assertEquals("d8", state.largest.first().id)
    }

    @Test
    fun duplicateGroupsNeedTwoLiveDocuments() {
        val docs = listOf(doc("a"), doc("b"), doc("c"))
        val state = buildStorageState(null, docs, listOf(listOf("a", "b"), listOf("c", "gone")))
        assertEquals(1, state.duplicates.size)
        assertEquals(listOf("a", "b"), state.duplicates.single().documents.map { it.id })
    }
}
