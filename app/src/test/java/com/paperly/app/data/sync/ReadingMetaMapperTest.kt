package com.paperly.app.data.sync

import com.paperly.app.domain.sync.ReadingMeta
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingMetaMapperTest {
    private val meta = ReadingMeta("d1", "12", 0.25f, 99L, 1)

    private val epub = "{\"href\":\"c1.xhtml\",\"type\":\"application/xhtml+xml\",\"title\":\"Chapter One\"," +
        "\"locations\":{\"progression\":0.4,\"position\":7,\"totalProgression\":0.1,\"fragments\":[\"p3\"]}," +
        "\"text\":{\"before\":\"secret before\",\"highlight\":\"secret words\",\"after\":\"secret after\"}}"

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(meta, readingMetaFromMap("d1", meta.toFirestoreMap()))
    }

    @Test
    fun progressIsClampedIntoZeroToOneBeforeSending() {
        assertEquals(1.0, meta.copy(progressPercent = 7f).toFirestoreMap()[ReadingFields.PROGRESS] as Double, 0.0)
        assertEquals(0.0, meta.copy(progressPercent = -1f).toFirestoreMap()[ReadingFields.PROGRESS] as Double, 0.0)
    }

    @Test
    fun badCloudDataIsSkippedNotCrashed() {
        val good: Map<String, Any?> = meta.toFirestoreMap()
        assertNull(readingMetaFromMap("d1", good - ReadingFields.LOCATOR))
        assertNull(readingMetaFromMap("d1", good + (ReadingFields.UPDATED_AT to "yesterday")))
        assertNull(readingMetaFromMap("d1", good + (ReadingFields.PROGRESS to null)))
        assertNull(readingMetaFromMap("d1", good + (ReadingFields.LOCATOR to "  ")))
    }

    @Test
    fun missingSchemaVersionDefaultsToOne() {
        val data = meta.toFirestoreMap() - ReadingFields.SCHEMA_VERSION
        assertEquals(1, readingMetaFromMap("d1", data)?.schemaVersion)
    }

    @Test
    fun pdfPageNumberIsKeptAsIs() {
        assertEquals("12", syncableLocator("12"))
        assertEquals("0", syncableLocator(" 0 "))
    }

    @Test
    fun epubLocatorLosesBookTextTitleAndFragments() {
        val clean = syncableLocator(epub)
        assertNotNull(clean)
        val text = clean.orEmpty()
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("Chapter One"))
        assertFalse(text.contains("p3"))
        val json = JSONObject(text)
        assertEquals("c1.xhtml", json.getString("href"))
        assertEquals("application/xhtml+xml", json.getString("type"))
        assertEquals(0.4, json.getJSONObject("locations").getDouble("progression"), 0.0)
        assertEquals(7, json.getJSONObject("locations").getInt("position"))
        assertFalse(json.has("text"))
    }

    @Test
    fun unusableLocatorsAreNotSent() {
        assertNull(syncableLocator(""))
        assertNull(syncableLocator("not json"))
        assertNull(syncableLocator("{\"type\":\"x\"}")) // no href
        assertNull(syncableLocator("1234567890")) // absurd page number
    }
}
