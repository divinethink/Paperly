package com.paperly.app.core.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashMarkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun consumeReturnsNullWhenNoMarker() {
        assertNull(CrashMarker.forApp(tmp.root).consume())
    }

    @Test
    fun recordThenConsumeReturnsInfoOnceAndClears() {
        val marker = CrashMarker.forApp(tmp.root)
        marker.record(1234L, IllegalStateException("secret document text"))

        val info = marker.consume()
        assertEquals(CrashInfo(1234L, "java.lang.IllegalStateException"), info)
        assertNull(marker.consume())
        assertFalse(File(tmp.root, "last_crash.marker").exists())
    }

    @Test
    fun markerNeverStoresExceptionMessage() {
        CrashMarker.forApp(tmp.root).record(1L, RuntimeException("private content"))
        assertFalse(File(tmp.root, "last_crash.marker").readText().contains("private content"))
    }

    @Test
    fun corruptMarkerIsIgnoredAndRemoved() {
        val file = File(tmp.root, "last_crash.marker").apply { writeText("not-a-number") }
        assertNull(CrashMarker(file).consume())
        assertFalse(file.exists())
    }
}
