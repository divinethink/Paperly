package com.paperly.app.domain.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    private val bills = Folder("bills", "Bills")
    private val y2026 = Folder("y26", "2026", parentId = "bills")
    private val oct = Folder("oct", "Oct", parentId = "y26")
    private val study = Folder("study", "Study")
    private val all = listOf(bills, y2026, oct, study)

    @Test fun pathListsTopLevelFirstAndTheFolderLast() {
        assertEquals(listOf("bills", "y26", "oct"), all.pathTo("oct").map { it.id })
        assertEquals("Bills › 2026 › Oct", all.pathLabel("oct"))
        assertEquals("Study", all.pathLabel("study"))
    }

    @Test fun unknownFolderHasAnEmptyPath() {
        assertEquals(emptyList<Folder>(), all.pathTo("nope"))
        assertEquals(emptyList<Folder>(), all.pathTo(null))
    }

    @Test fun aMissingParentMakesTheFolderTopLevel() {
        val orphan = Folder("o", "Orphan", parentId = "gone")
        val list = all + orphan
        assertEquals(listOf("o"), list.pathTo("o").map { it.id })
        assertEquals(listOf("bills", "study", "o"), list.childrenOf(null).map { it.id })
    }

    @Test fun aCycleInBadDataNeverLoops() {
        val a = Folder("a", "A", parentId = "b")
        val b = Folder("b", "B", parentId = "a")
        assertEquals(2, listOf(a, b).pathTo("a").size)
        assertEquals(setOf("a", "b"), listOf(a, b).subtreeIds("a"))
    }

    @Test fun childrenAreOnlyTheOnesDirectlyInside() {
        assertEquals(listOf("bills", "study"), all.childrenOf(null).map { it.id })
        assertEquals(listOf("y26"), all.childrenOf("bills").map { it.id })
        assertEquals(listOf("oct"), all.childrenOf("y26").map { it.id })
    }

    @Test fun nestingStopsAtThreeLevels() {
        assertTrue(all.canNestUnder(null))
        assertTrue(all.canNestUnder("bills"))
        assertTrue(all.canNestUnder("y26"))
        assertFalse(all.canNestUnder("oct")) // oct is level 3
        assertFalse(all.canNestUnder("missing"))
    }

    @Test fun subtreeHasTheFolderAndEverythingBelowIt() {
        assertEquals(setOf("bills", "y26", "oct"), all.subtreeIds("bills"))
        assertEquals(setOf("oct"), all.subtreeIds("oct"))
        assertEquals(setOf("gone"), all.subtreeIds("gone"))
    }
}
