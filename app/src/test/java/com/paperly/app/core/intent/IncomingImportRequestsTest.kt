package com.paperly.app.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingImportRequestsTest {
    @Test
    fun consumeClearsTheMatchingRequest() {
        val requests = IncomingImportRequests()
        requests.post("content://a")
        requests.consume("content://a")
        assertNull(requests.pending.value)
    }

    @Test
    fun consumeNeverDropsANewerRequest() {
        val requests = IncomingImportRequests()
        requests.post("content://a")
        requests.post("content://b")
        requests.consume("content://a")
        assertEquals("content://b", requests.pending.value)
    }
}
