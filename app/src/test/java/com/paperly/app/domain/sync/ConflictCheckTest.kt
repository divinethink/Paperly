package com.paperly.app.domain.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictCheckTest {
    @Test
    fun writesWhenTheCloudIsStillAtTheVersionWeLastSaw() =
        assertTrue(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = 5, localUpdatedAt = 9))

    @Test
    fun conflictWhenAnotherDeviceWroteSinceWeLastSaw() =
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = 7, localUpdatedAt = 9))

    @Test
    fun conflictEvenIfOurClockIsAheadOfTheOtherDevice() =
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = 6, localUpdatedAt = 99))

    @Test
    fun conflictWhenTheCloudCopyWasDeletedElsewhere() =
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = null, localUpdatedAt = 9))

    @Test
    fun ourOwnEarlierWriteIsNotAConflict() =
        assertTrue(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = 9, localUpdatedAt = 9))

    @Test
    fun noBaseAndNoCloudCopyWrites() =
        assertTrue(ConflictCheck.canWrite(baseUpdatedAt = null, remoteUpdatedAt = null, localUpdatedAt = 9))

    @Test
    fun noBaseWritesOverAnOlderOrEqualCloudCopy() {
        assertTrue(ConflictCheck.canWrite(baseUpdatedAt = null, remoteUpdatedAt = 4, localUpdatedAt = 9))
        assertTrue(ConflictCheck.canWrite(baseUpdatedAt = null, remoteUpdatedAt = 9, localUpdatedAt = 9))
    }

    @Test
    fun noBaseAndANewerCloudCopyIsAConflict() =
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = null, remoteUpdatedAt = 10, localUpdatedAt = 9))

    @Test
    fun unreadableCloudVersionIsAlwaysAConflict() {
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = null, remoteUpdatedAt = Long.MAX_VALUE, localUpdatedAt = 9))
        assertFalse(ConflictCheck.canWrite(baseUpdatedAt = 5, remoteUpdatedAt = Long.MAX_VALUE, localUpdatedAt = 9))
    }
}
