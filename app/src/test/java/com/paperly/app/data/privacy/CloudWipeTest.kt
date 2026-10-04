package com.paperly.app.data.privacy

import androidx.room.Room
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.data.sync.CleanupAuth
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.privacy.CloudWipeBlock
import com.paperly.app.domain.privacy.CloudWipeResult
import com.paperly.app.domain.sync.CloudListing
import com.paperly.app.domain.sync.RemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudWipeTest {
    private lateinit var db: PaperlyDatabase
    private val cloud = FakeCloud()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun wipe(auth: AuthState = AuthState.SignedIn("u1", null)) = runBlocking {
        CloudWipeImpl(CleanupAuth(auth), db.syncItemDao(), cloud, cloud, cloud, cloud).wipe()
    }

    @Test
    fun `signed out does nothing`() {
        cloud.seed("a")
        assertEquals(CloudWipeResult.NotSignedIn, wipe(AuthState.SignedOut))
        assertFalse(cloud.isEmpty)
    }

    @Test
    fun `deletes files documents and readings`() {
        cloud.seed("a", "b")
        cloud.fileIds += "only-file"
        cloud.readingIds += "only-reading"
        assertEquals(CloudWipeResult.Wiped, wipe())
        assertTrue(cloud.isEmpty)
    }

    @Test
    fun `second run on an empty cloud is still wiped`() {
        assertEquals(CloudWipeResult.Wiped, wipe())
        assertEquals(CloudWipeResult.Wiped, wipe())
    }

    @Test
    fun `drive access missing stops before deleting anything`() {
        cloud.seed("a")
        cloud.listing = { CloudListing.NeedsConsent }
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.NEEDS_ACCESS), wipe())
        assertEquals(setOf("a"), cloud.docIds)
    }

    @Test
    fun `listing failure is offline`() {
        cloud.seed("a")
        cloud.failList = true
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.OFFLINE_OR_FAILED), wipe())
    }

    @Test
    fun `denied and retry map to their reasons`() {
        cloud.seed("a")
        cloud.docDelete = RemoteResult.DENIED
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.DENIED), wipe())
        cloud.docDelete = RemoteResult.RETRY
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.OFFLINE_OR_FAILED), wipe())
        cloud.docDelete = RemoteResult.OK
        cloud.fileDelete = RemoteResult.DEFERRED
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.NEEDS_ACCESS), wipe())
    }

    @Test
    fun `leftovers after the delete are not reported as success`() {
        cloud.seed("a")
        cloud.ignoreReadingDelete = true
        assertEquals(CloudWipeResult.Stopped(CloudWipeBlock.NOT_EMPTY_AFTER), wipe())
    }
}
