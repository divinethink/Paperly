package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPageOpsTest {
    private fun pages(n: Int, lockedAt: Set<Int> = emptySet()) =
        List(n) { ScanEditPage(index = it, locked = it in lockedAt) }
    private fun added(name: String) = ScanEditPage(index = ADDED_INDEX, added = name)

    @Test fun moveShiftsOnePageAndKeepsTheRest() {
        assertEquals(listOf(1, 2, 0, 3), pages(4).moved(0, 2).map { it.index })
        assertEquals(listOf(0, 3, 1, 2), pages(4).moved(3, 1).map { it.index })
    }

    @Test fun badMoveIsANoOp() {
        val p = pages(3)
        assertSame(p, p.moved(0, 0))
        assertSame(p, p.moved(-1, 1))
        assertSame(p, p.moved(0, 3))
    }

    @Test fun highlightedAndLastPageCannotBeDeleted() {
        val p = pages(3, lockedAt = setOf(1))
        assertTrue(p.canDeleteAt(0))
        assertFalse(p.canDeleteAt(1))
        assertSame(p, p.withoutAt(1))
        assertFalse(pages(1).canDeleteAt(0))
    }

    @Test fun deleteRemovesOnlyThatPage() {
        assertEquals(listOf(0, 2), pages(3).withoutAt(1).map { it.index })
    }

    @Test fun severalPagesAreDeletedTogether() {
        assertEquals(listOf(1, 3), pages(5).withoutAll(setOf(0, 2, 4)).map { it.index })
    }

    @Test fun deleteAllRefusesLockedEveryPageEmptyAndBadPositions() {
        val p = pages(3, lockedAt = setOf(1))
        assertFalse(p.canDeleteAll(setOf(0, 1)))
        assertFalse(pages(3).canDeleteAll(setOf(0, 1, 2)))
        assertFalse(pages(3).canDeleteAll(emptySet()))
        assertFalse(pages(3).canDeleteAll(setOf(3)))
        assertFalse(pages(3).canDeleteAll(setOf(-1)))
        assertSame(p, p.withoutAll(setOf(0, 1)))
        assertTrue(p.canDeleteAll(setOf(0, 2)))
    }

    @Test fun structuralOnlyWhenOrderCountOrContentChanges() {
        assertFalse(pages(3).isStructural(3))
        assertTrue(pages(3).moved(0, 1).isStructural(3))
        assertTrue(pages(3).withoutAt(2).isStructural(3))
        assertTrue((pages(3) + added("a1")).isStructural(3))
    }

    @Test fun positionMapSendsOldIndexToNewPosition() {
        val plan = pages(4).moved(0, 2).withoutAt(3) + added("x") // [1, 2, 0] + added
        assertEquals(mapOf(1 to 0, 2 to 1, 0 to 2), plan.positionMap())
    }

    @Test fun planMustKeepHighlightedPagesAndStayConsistent() {
        val stored = pages(3, lockedAt = setOf(1))
        assertTrue(validPlan(pages(3).moved(0, 2), stored))
        assertTrue(validPlan(pages(3) + added("a1"), stored))
        assertFalse(validPlan(pages(3).withoutAt(0).filter { it.index != 1 }, stored)) // dropped a highlighted page
        assertFalse(validPlan(emptyList(), stored))
        assertFalse(validPlan(listOf(pages(3)[0], pages(3)[0], pages(3)[1]), stored)) // duplicate source
        assertFalse(validPlan(pages(3) + ScanEditPage(index = 7), stored)) // unknown source
        assertFalse(validPlan(pages(3) + added("a1") + added("a1"), stored)) // same staged file twice
        assertFalse(validPlan(pages(3) + added(""), stored))
    }
}
