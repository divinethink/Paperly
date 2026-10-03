package com.paperly.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PullDecisionTest {
    private fun remote(updatedAt: Long, deletedAt: Long? = null) = DocumentMeta(
        documentId = "d1",
        title = "T",
        type = "pdf",
        sizeBytes = 1,
        checksum = "c",
        createdAt = 1,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun decide(local: Long?, base: Long?, pending: Boolean, remote: DocumentMeta) =
        PullDecision.decide(local, base, pending, remote)

    @Test
    fun newDocumentIsInserted() = assertEquals(PullAction.INSERT, decide(null, null, false, remote(5)))

    @Test
    fun newDocumentThatIsInTrashInTheCloudIsNotBroughtBack() =
        assertEquals(PullAction.SKIP, decide(null, null, false, remote(5, deletedAt = 4)))

    @Test
    fun newDocumentWithAPendingItemIsLeftAlone() = assertEquals(PullAction.SKIP, decide(null, null, true, remote(5)))

    @Test
    fun newerCloudCopyIsAppliedWhenNothingIsPending() = assertEquals(PullAction.APPLY, decide(3, 3, false, remote(5)))

    @Test
    fun anUnsyncedLocalChangeIsNeverOverwritten() = assertEquals(PullAction.SKIP, decide(3, 3, true, remote(5)))

    @Test
    fun alreadySeenVersionIsSkipped() = assertEquals(PullAction.SKIP, decide(5, 5, false, remote(5)))

    @Test
    fun sameVersionWithoutABaseIsOnlyRemembered() = assertEquals(PullAction.MARK_SEEN, decide(5, null, false, remote(5)))

    @Test
    fun olderCloudCopyIsIgnored() = assertEquals(PullAction.SKIP, decide(9, 3, false, remote(5)))
}
