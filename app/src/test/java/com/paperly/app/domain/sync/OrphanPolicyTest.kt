package com.paperly.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrphanPolicyTest {
    private val hour = 60L * 60 * 1000
    private val now = 1000 * hour

    private fun file(id: String, doc: String, ageHours: Long) = CloudFile(id, doc, 10, now - ageHours * hour)

    private fun orphans(cloud: List<CloudFile>, local: Set<String> = emptySet(), remote: Set<String> = emptySet()) =
        OrphanPolicy.orphans(cloud, local, remote, now).map { it.fileId }

    @Test
    fun oldFileOfAnUnknownDocumentIsAnOrphan() {
        assertEquals(listOf("f1"), orphans(listOf(file("f1", "d1", 25))))
    }

    @Test
    fun aDocumentKnownLocallyOrInTheCloudIsNeverAnOrphan() {
        val cloud = listOf(file("f1", "d1", 72), file("f2", "d2", 72), file("f3", "d3", 72))
        assertEquals(listOf("f3"), orphans(cloud, local = setOf("d1"), remote = setOf("d2")))
    }

    @Test
    fun theGracePeriodIsTwentyFourHoursExclusive() {
        assertTrue(orphans(listOf(file("f1", "d1", 24))).isEmpty())
        assertTrue(orphans(listOf(CloudFile("f2", "d2", 1, now - OrphanPolicy.GRACE_MS))).isEmpty())
        assertEquals(listOf("f3"), orphans(listOf(CloudFile("f3", "d3", 1, now - OrphanPolicy.GRACE_MS - 1))))
    }

    @Test
    fun oneRecentCopyProtectsEveryCopyOfThatDocument() {
        val cloud = listOf(file("old", "d1", 72), file("new", "d1", 1), file("other", "d2", 72))
        assertEquals(listOf("other"), orphans(cloud))
    }

    @Test
    fun anUnknownCreationTimeIsNeverTreatedAsOld() {
        assertTrue(orphans(listOf(CloudFile("f1", "d1", 10, 0))).isEmpty())
    }

    @Test
    fun anEmptyCloudHasNothingToRemove() {
        assertTrue(orphans(emptyList()).isEmpty())
    }
}
