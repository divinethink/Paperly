package com.paperly.app.data.sync

import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.sync.RemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingPushStepTest {
    private lateinit var db: PaperlyDatabase
    private val remote = FakeReadingRemote()
    private lateinit var step: ReadingPushStep

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        step = ReadingPushStep(db.readerDao(), remote)
    }

    @After
    fun tearDown() = db.close()

    private fun item(op: String = SyncOperation.PUT) = SyncItemEntity(
        syncId = "reading:d1",
        entityType = SyncEntityType.READING,
        entityId = "d1",
        operation = op,
        state = SyncItemState.QUEUED,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private suspend fun state(locator: String, progress: Float = 0.5f, at: Long = 50L) {
        val doc = DocumentEntity(
            documentId = "d1",
            title = "T",
            type = "pdf",
            sizeBytes = 1,
            checksum = "c",
            createdAt = 1,
            updatedAt = 2,
        )
        db.documentDao().insert(doc)
        db.readerDao().upsertState(ReadingStateEntity("d1", locator, progress, at))
    }

    @Test
    fun sendsTheStoredPositionWithItsOwnTimestamp() = runBlocking {
        state("12", 0.4f, 50L)
        assertEquals(RemoteResult.OK, step.sync("u1", item()))
        val sent = remote.puts.single()
        assertEquals("12", sent.locator)
        assertEquals(50L, sent.updatedAt)
        assertEquals(0.4f, sent.progressPercent, 0.0001f)
    }

    @Test
    fun missingPositionIsDoneAndNeverBecomesARemoteDelete() = runBlocking {
        assertEquals(RemoteResult.OK, step.sync("u1", item()))
        assertTrue(remote.puts.isEmpty())
        assertTrue(remote.deletes.isEmpty())
    }

    @Test
    fun unreadableLocatorIsDoneWithoutSendingAnything() = runBlocking {
        state("garbage")
        assertEquals(RemoteResult.OK, step.sync("u1", item()))
        assertTrue(remote.puts.isEmpty())
    }

    @Test
    fun olderPositionNeverOverwritesANewerCloudOne() = runBlocking {
        state("12", at = 50L)
        remote.cloud["d1"] = 80L
        assertEquals(RemoteResult.OK, step.sync("u1", item()))
        assertTrue(remote.puts.isEmpty())
        assertEquals(80L, remote.cloud["d1"])
    }

    @Test
    fun deleteItemDeletesTheRemotePosition() = runBlocking {
        assertEquals(RemoteResult.OK, step.sync("u1", item(SyncOperation.DELETE)))
        assertEquals(listOf("d1"), remote.deletes)
    }

    @Test
    fun transientFailureIsReportedSoTheQueueRetries() = runBlocking {
        state("12")
        remote.result = RemoteResult.RETRY
        assertEquals(RemoteResult.RETRY, step.sync("u1", item()))
    }
}
