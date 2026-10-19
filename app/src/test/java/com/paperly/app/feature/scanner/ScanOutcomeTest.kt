package com.paperly.app.feature.scanner

import com.paperly.app.domain.document.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOutcomeTest {
    @Test fun successFinishesAndOnlyAGalleryImportOpensTheEditor() {
        val camera = scanOutcome(ImportResult.Success("d1"), openEditor = false)
        assertTrue(camera.saved)
        assertNull(camera.editDocumentId)
        assertEquals("d1", scanOutcome(ImportResult.Success("d1"), openEditor = true).editDocumentId)
    }

    @Test fun duplicateFinishesAndSaysSo() {
        val outcome = scanOutcome(ImportResult.Duplicate("x", "Old"), openEditor = true)
        assertTrue(outcome.saved)
        assertTrue(outcome.duplicate)
        assertNull(outcome.editDocumentId)
    }

    @Test fun failureIsAnErrorNotASavedScan() {
        for (result in listOf(ImportResult.Failed, ImportResult.UnsupportedType)) {
            val outcome = scanOutcome(result, openEditor = false)
            assertFalse(outcome.saved)
            assertFalse(outcome.saving)
            assertEquals(ScanError.Save, outcome.error)
        }
    }

    @Test fun openingTheScreenClearsAStuckStateUnlessAJobReallyRuns() {
        val stuck = ScanUiState(saving = true, error = ScanError.Save, saved = true)
        assertEquals(ScanUiState(), stuck.onScreenEnter(workRunning = false))
        assertEquals(stuck, stuck.onScreenEnter(workRunning = true))
    }
}
