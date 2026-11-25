package com.paperly.app.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubKeepPositionTest {
    @Test
    fun aSmallShiftIsNotADrift() {
        assertFalse(progressionDrifted(0.40, 0.41))
    }

    @Test
    fun aBigShiftIsADrift() {
        assertTrue(progressionDrifted(0.40, 0.65))
        assertTrue(progressionDrifted(0.65, 0.40))
    }

    @Test
    fun anUnknownPositionIsNeverADrift() {
        assertFalse(progressionDrifted(null, 0.5))
        assertFalse(progressionDrifted(0.5, null))
    }
}
