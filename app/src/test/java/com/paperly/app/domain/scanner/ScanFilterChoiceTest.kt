package com.paperly.app.domain.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScanFilterChoiceTest {
    @Test fun offeredFiltersAreOriginalDocsClean() {
        assertEquals(
            listOf(ScanFilter.ORIGINAL, ScanFilter.DOCS, ScanFilter.CLEAN),
            SCAN_FILTER_CHOICES,
        )
        // B/W is its own button; the old COLOR/GRAY still exist (older pages) but are not offered
        assertFalse(ScanFilter.BW in SCAN_FILTER_CHOICES)
        assertFalse(ScanFilter.COLOR in SCAN_FILTER_CHOICES)
        assertFalse(ScanFilter.GRAY in SCAN_FILTER_CHOICES)
        assertFalse(ScanFilter.PHOTO in SCAN_FILTER_CHOICES)
        assertFalse(ScanFilter.AUTO in SCAN_FILTER_CHOICES)
    }

    @Test fun bwButtonGoesToBwAndBackToThePreviousFilter() {
        assertEquals(ScanFilter.BW, toggledBw(ScanFilter.DOCS, null))
        assertEquals(ScanFilter.DOCS, toggledBw(ScanFilter.BW, ScanFilter.DOCS))
    }

    @Test fun bwButtonWithoutAMemoryFallsBackToOriginal() {
        assertEquals(ScanFilter.ORIGINAL, toggledBw(ScanFilter.BW, null))
        assertEquals(ScanFilter.ORIGINAL, toggledBw(ScanFilter.BW, ScanFilter.BW))
    }

    @Test fun savedSizeNameFallsBackToMedium() {
        assertEquals(ScanOutputSize.LARGE, scanOutputSizeOf("LARGE"))
        assertEquals(ScanOutputSize.SMALL, scanOutputSizeOf("SMALL"))
        assertEquals(ScanOutputSize.MEDIUM, scanOutputSizeOf(null))
        assertEquals(ScanOutputSize.MEDIUM, scanOutputSizeOf("HUGE"))
    }
}
