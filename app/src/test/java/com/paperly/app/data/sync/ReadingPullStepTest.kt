package com.paperly.app.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.sync.ReadingMeta
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingPullStepTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase
    private val remote = FakeReadingRemote()
    private lateinit var step: ReadingPullStep
    private lateinit var settings: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private val marker = longPreferencesKey("sync_last_pull_reading")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined + Job())) {
            File(tmp.newFolder(), "s.preferences_pb")
        }
        step = ReadingPullStep(db.documentDao(), db.readerDao(), db.syncItemDao(), remote, settings)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun doc(id: String) = db.documentDao().insert(
        DocumentEntity(documentId = id, title = "T", type = "pdf", sizeBytes = 1, checksum = "c", createdAt = 1, updatedAt = 2),
    )

    private fun cloud(id: String, locator: String, at: Long) {
        remote.stored[id] = ReadingMeta(id, locator, 0.5f, at)
    }

    private suspend fun local(id: String) = db.readerDao().getState(id)

    @Test
    fun newerCloudPositionIsAppliedWithItsTimestamp() = runBlocking {
        doc("d1")
        db.readerDao().upsertState(ReadingStateEntity("d1", "2", 0.1f, 100L))
        cloud("d1", "9", 200L)
        assertTrue(step.pull("u1"))
        assertEquals("9", local("d1")?.locator)
        assertEquals(200L, local("d1")?.lastOpenedAt)
    }

    @Test
    fun positionIsAppliedWhenNothingWasReadHereYet() = runBlocking {
        doc("d1")
        cloud("d1", "4", 200L)
        step.pull("u1")
        assertEquals("4", local("d1")?.locator)
    }

    @Test
    fun newerOrEqualLocalPositionIsKept() = runBlocking {
        doc("d1")
        db.readerDao().upsertState(ReadingStateEntity("d1", "7", 0.7f, 300L))
        cloud("d1", "1", 200L)
        step.pull("u1")
        assertEquals("7", local("d1")?.locator)
    }

    @Test
    fun unsentLocalPositionIsNeverOverwritten() = runBlocking {
        doc("d1")
        db.readerDao().upsertState(ReadingStateEntity("d1", "2", 0.1f, 100L))
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.PUT, 10L)
        cloud("d1", "9", 200L)
        step.pull("u1")
        assertEquals("2", local("d1")?.locator)
    }

    @Test
    fun applyingTwiceChangesNothingAndQueuesNothing() = runBlocking {
        doc("d1")
        cloud("d1", "9", 200L)
        step.pull("u1")
        step.pull("u1")
        assertEquals("9", local("d1")?.locator)
        assertEquals(0, db.syncItemDao().countItem("reading:d1"))
    }

    @Test
    fun positionOfAnUnknownDocumentWaitsUntilTheDocumentArrives() = runBlocking {
        doc("d1")
        cloud("d9", "3", 50L)
        cloud("d1", "9", 60L)
        assertTrue(step.pull("u1"))
        assertNull(local("d9"))
        assertEquals("9", local("d1")?.locator)
        assertEquals(49L, settings.data.first()[marker]) // held back so d9 is fetched again
        doc("d9")
        step.pull("u1")
        assertEquals("3", local("d9")?.locator)
        assertEquals(60L, settings.data.first()[marker])
    }

    @Test
    fun cloudLocatorIsCleanedAndUnusableOnesAreIgnored() = runBlocking {
        doc("d1")
        doc("d2")
        cloud("d1", "{\"href\":\"a.xhtml\",\"locations\":{\"progression\":0.2},\"text\":{\"highlight\":\"secret\"}}", 10L)
        cloud("d2", "garbage", 10L)
        step.pull("u1")
        assertFalse(local("d1")?.locator.orEmpty().contains("secret"))
        assertTrue(local("d1")?.locator.orEmpty().contains("a.xhtml"))
        assertNull(local("d2"))
    }

    @Test
    fun offlineChangesNothingAndReportsFailure() = runBlocking {
        doc("d1")
        cloud("d1", "9", 200L)
        remote.failFetch = true
        assertFalse(step.pull("u1"))
        assertNull(local("d1"))
        assertNull(settings.data.first()[marker])
    }

    @Test
    fun resetForgetsTheMarker() = runBlocking {
        doc("d1")
        cloud("d1", "9", 200L)
        step.pull("u1")
        assertEquals(200L, settings.data.first()[marker])
        step.reset()
        assertNull(settings.data.first()[marker])
    }
}
