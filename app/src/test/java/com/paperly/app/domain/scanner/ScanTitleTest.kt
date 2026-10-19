package com.paperly.app.domain.scanner

import com.paperly.app.domain.document.MAX_TITLE_LENGTH
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanTitleTest {
    @Test fun trimsInput() = assertEquals("Lab notes", scanTitle("  Lab notes  ", "Scan"))

    @Test fun blankFallsBackToDefault() = assertEquals("Scan 1", scanTitle("   ", "Scan 1"))

    @Test fun emptyFallsBackToDefault() = assertEquals("Scan 1", scanTitle("", "Scan 1"))

    @Test fun capsLength() = assertEquals(MAX_TITLE_LENGTH, scanTitle("a".repeat(MAX_TITLE_LENGTH + 50), "x").length)

    @Test fun newScanTitleIsScanPlusDateAndTime() {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(2026, Calendar.OCTOBER, 20, 14, 5)
        val at = calendar.time
        assertEquals("Scan 2026-10-20 14-05", newScanTitle(at))
    }
}
