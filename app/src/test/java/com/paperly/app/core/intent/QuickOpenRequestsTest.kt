package com.paperly.app.core.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickOpenRequestsTest {
    @Test
    fun closeOnLeaveIsSingleUse() {
        val requests = QuickOpenRequests()
        requests.post("content://a", startedActivity = true)
        requests.markCloseOnLeave("r1")
        assertTrue(requests.takeCloseOnLeave("r1"))
        assertFalse(requests.takeCloseOnLeave("r1"))
    }

    @Test
    fun otherReaderIsNeverAffected() {
        val requests = QuickOpenRequests()
        requests.markCloseOnLeave("r1")
        assertFalse(requests.takeCloseOnLeave("r2"))
    }

    @Test
    fun newRequestClearsAnOldMark() {
        val requests = QuickOpenRequests()
        requests.markCloseOnLeave("r1")
        requests.post("content://b")
        assertFalse(requests.startedActivity)
        assertFalse(requests.takeCloseOnLeave("r1"))
    }
}
