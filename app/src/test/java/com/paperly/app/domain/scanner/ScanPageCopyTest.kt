package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPageCopyTest {
    private val cropped =
        ScanQuad(ScanPoint(0.1f, 0.1f), ScanPoint(0.9f, 0.1f), ScanPoint(0.9f, 0.9f), ScanPoint(0.1f, 0.9f))
    private fun pages(n: Int) = List(n) { ScanEditPage(index = it) }

    @Test fun pickedGivesPagesInPageOrder() {
        assertEquals(listOf(0, 2, 3), pages(5).picked(setOf(3, 0, 2))?.map { it.index })
    }

    @Test fun pickedRefusesNothingTickedAndMissingPositions() {
        assertNull(pages(3).picked(emptySet()))
        assertNull(pages(3).picked(setOf(0, 3)))
        assertNull(pages(3).picked(setOf(-1)))
    }

    @Test fun copyAsAddedPageKeepsCropRotationAndFilterButNotTheLock() {
        val source = ScanEditPage(
            index = 4,
            rotation = 90,
            quad = cropped,
            filter = ScanFilter.COLOR,
            brightness = 20,
            contrast = -10,
            locked = true,
        )
        val copy = source.copiedAs("a1.img")
        assertEquals(ADDED_INDEX, copy.index)
        assertEquals("a1.img", copy.added)
        assertEquals(90, copy.rotation)
        assertEquals(cropped, copy.quad)
        assertEquals(ScanFilter.COLOR, copy.filter)
        assertEquals(20, copy.brightness)
        assertEquals(-10, copy.contrast)
        assertFalse(copy.locked)
    }

    @Test fun copiedPagesAreAValidPlanAppendedToTheTarget() {
        val target = pages(3)
        val added = pages(2).mapIndexed { i, page -> page.copiedAs("a$i.img") }
        assertTrue(validPlan(target + added, target))
        assertEquals(setOf(0, 1, 2), (target + added).positionMap().keys)
    }

    @Test fun copyAtPositionIsAKeptPageOfANewDocument() {
        val copy = ScanEditPage(index = 7, filter = ScanFilter.COLOR, locked = true).copiedAt(1)
        assertEquals(1, copy.index)
        assertNull(copy.added)
        assertEquals(ScanFilter.COLOR, copy.filter)
        assertFalse(copy.locked)
        assertTrue(validPlan(List(2) { ScanEditPage(index = it).copiedAt(it) }, List(2) { ScanEditPage(index = it) }))
    }

    @Test fun editedMeansAnythingBeyondThePlainOriginal() {
        assertFalse(ScanEditPage(index = 0).isEdited())
        assertTrue(ScanEditPage(index = 0, rotation = 90).isEdited())
        assertTrue(ScanEditPage(index = 0, quad = cropped).isEdited())
        assertTrue(ScanEditPage(index = 0, filter = ScanFilter.COLOR).isEdited())
        assertTrue(ScanEditPage(index = 0, brightness = 5).isEdited())
        assertTrue(ScanEditPage(index = 0, contrast = -5).isEdited())
    }
}
