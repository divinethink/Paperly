package com.paperly.app.domain.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {
    @Test
    fun totalIsActivePlusTrash() {
        assertEquals(350L, StorageUsage(activeBytes = 300, activeCount = 2, trashBytes = 50, trashCount = 1).totalBytes)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0L, StorageUsage(0, 0, 0, 0).totalBytes)
    }
}
