package com.paperly.app.core.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickViewTokenTest {
    @Test
    fun generatedTokenIsAccepted() {
        assertTrue(QuickViewStore.isToken("quick-123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun libraryIdsAndUnsafeValuesAreRejected() {
        assertFalse(QuickViewStore.isToken("123e4567-e89b-12d3-a456-426614174000")) // a Library document id
        assertFalse(QuickViewStore.isToken("quick-../secret"))
        assertFalse(QuickViewStore.isToken("quick-a/b"))
        assertFalse(QuickViewStore.isToken(""))
    }
}
