package com.paperly.app.data.sync

import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.sync.CleanupBlock
import com.paperly.app.domain.sync.CleanupPreview
import com.paperly.app.domain.sync.CleanupResult
import com.paperly.app.domain.sync.CloudFile
import com.paperly.app.domain.sync.CloudListing
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
class OrphanCleanupTest {
    private val hour = 60L * 60 * 1000
    private val now = 1000 * hour
    private lateinit var db: PaperlyDatabase
    private val settings = CleanupSettings()
    private val inventory = CleanupInventory(CloudListing.Complete(emptyList()))
    private val remote = CleanupRemote()
    private val files = CleanupFiles()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun cleanup(auth: AuthState = AuthState.SignedIn("u1", null)) =
        OrphanCleanup(CleanupAuth(auth), settings, db.syncItemDao(), inventory, remote, files)

    /** Cloud files as (document id to age in hours). */
    private fun cloud(vararg docs: Pair<String, Long>) {
        inventory.listing = CloudListing.Complete(
            docs.map { (doc, ageHours) -> CloudFile("f-$doc", doc, 100, now - ageHours * hour) },
        )
    }

    private suspend fun localDoc(id: String) = db.documentDao().insert(
        DocumentEntity(
            documentId = id,
            title = "T",
            type = "pdf",
            sizeBytes = 1,
            checksum = "c",
            createdAt = 1,
            updatedAt = 2,
        ),
    )

    @Test
    fun onlyOldFilesOfDocumentsKnownNowhereAreFound() = runBlocking {
        localDoc("local")
        remote.ids = setOf("inCloud")
        cloud("local" to 72L, "inCloud" to 72L, "recent" to 2L, "orphan1" to 30L, "orphan2" to 96L)
        assertEquals(CleanupPreview.Found(2, 200), cleanup().preview(now))
        assertTrue(files.deletes.isEmpty()) // a preview never deletes
    }

    @Test
    fun cleanRemovesTheOrphansAndNothingElse() = runBlocking {
        localDoc("local")
        cloud("local" to 72L, "orphan1" to 30L, "orphan2" to 96L)
        assertEquals(CleanupResult.Done(2, 0), cleanup().clean(now))
        assertEquals(setOf("orphan1", "orphan2"), files.deletes.toSet())
    }

    @Test
    fun aDocumentThatAppearsBeforeTheFinalCheckKeepsItsFile() = runBlocking {
        cloud("orphan1" to 30L, "late" to 30L)
        remote.appearLater = setOf("late")
        assertEquals(CleanupResult.Done(1, 1), cleanup().clean(now))
        assertEquals(listOf("orphan1"), files.deletes)
    }

    @Test
    fun aFailedDeleteIsCountedAsLeftAndNotRetriedBlindly() = runBlocking {
        cloud("orphan1" to 30L)
        files.deleteResult = RemoteResult.RETRY
        assertEquals(CleanupResult.Done(0, 1), cleanup().clean(now))
    }

    @Test
    fun anIncompletePictureDeletesNothing() = runBlocking {
        cloud("orphan1" to 30L)
        inventory.listing = CloudListing.Failed
        assertEquals(CleanupResult.Blocked(CleanupBlock.UNAVAILABLE), cleanup().clean(now))
        inventory.listing = CloudListing.NeedsConsent
        assertEquals(CleanupResult.Blocked(CleanupBlock.NEEDS_ACCESS), cleanup().clean(now))
        cloud("orphan1" to 30L)
        remote.ids = null // the cloud document list could not be read
        assertEquals(CleanupResult.Blocked(CleanupBlock.UNAVAILABLE), cleanup().clean(now))
        assertTrue(files.deletes.isEmpty())
    }

    @Test
    fun notSignedInPausedOrBusySyncBlocksTheCleanUp() = runBlocking {
        cloud("orphan1" to 30L)
        assertEquals(
            CleanupPreview.Blocked(CleanupBlock.NOT_SIGNED_IN),
            cleanup(AuthState.SignedOut).preview(now),
        )
        settings.isPaused = true
        assertEquals(CleanupPreview.Blocked(CleanupBlock.PAUSED), cleanup().preview(now))
        settings.isPaused = false
        db.syncItemDao().enqueue(SyncEntityType.DOCUMENT, "x", SyncOperation.PUT, 10L)
        assertEquals(CleanupPreview.Blocked(CleanupBlock.SYNC_BUSY), cleanup().preview(now))
        assertTrue(files.deletes.isEmpty())
    }
}
