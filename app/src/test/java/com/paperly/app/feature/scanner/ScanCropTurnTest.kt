package com.paperly.app.feature.scanner

import androidx.compose.ui.geometry.Offset
import com.paperly.app.domain.scanner.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanCropTurnTest {
    @Test fun cornerFollowsTheTurnedPicture() {
        val topLeft = ScanPoint(0f, 0f)
        assertEquals(ScanPoint(0f, 0f), toShown(topLeft, 0))
        assertEquals(ScanPoint(1f, 0f), toShown(topLeft, 90)) // top-left goes to top-right
        assertEquals(ScanPoint(1f, 1f), toShown(topLeft, 180))
        assertEquals(ScanPoint(0f, 1f), toShown(topLeft, 270))
    }

    @Test fun dragIsTurnedBack() {
        assertEquals(Offset(0.2f, 0.1f), toStoredDrag(Offset(0.2f, 0.1f), 0))
        assertEquals(Offset(0.1f, -0.2f), toStoredDrag(Offset(0.2f, 0.1f), 90))
        assertEquals(Offset(-0.2f, -0.1f), toStoredDrag(Offset(0.2f, 0.1f), 180))
        assertEquals(Offset(-0.1f, 0.2f), toStoredDrag(Offset(0.2f, 0.1f), 270))
    }
}
