package com.paperly.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The disclosure list must never ship with a missing or empty text, or with two entries sharing one text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacyDisclosureTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun everyEntryHasNonEmptyDistinctTexts() {
        val items = PrivacyDisclosure.items
        assertEquals(6, items.size)
        val titles = items.map { context.getString(it.title) }
        assertEquals(titles.size, titles.toSet().size)
        items.forEach {
            assertTrue(context.getString(it.title).isNotBlank())
            assertTrue(context.getString(it.body).isNotBlank())
            assertTrue(context.getString(it.condition).isNotBlank())
        }
    }

    @Test
    fun coversEveryServiceTheAppTalksTo() {
        val all = PrivacyDisclosure.items.joinToString(" ") { context.getString(it.title) + context.getString(it.body) }
        listOf("Firebase Authentication", "Firestore", "Drive", "Crashlytics", "Google Play services", "never sent")
            .forEach { assertTrue("missing: $it", all.contains(it)) }
    }
}
