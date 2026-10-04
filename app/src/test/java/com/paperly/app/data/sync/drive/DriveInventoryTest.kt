package com.paperly.app.data.sync.drive

import com.paperly.app.domain.sync.CloudListing
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real listing code (paging, field parsing, error mapping) against the in-process fake Drive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveInventoryTest {
    private lateinit var drive: FakeDriveServer
    private val auth = FakeDriveAuth()

    @Before
    fun setUp() {
        drive = FakeDriveServer().start()
    }

    @After
    fun tearDown() = drive.stop()

    private fun inventory(pageSize: Int = 100) = DriveInventory(drive.endpoints, auth, pageSize)

    private fun list() = runBlocking { inventory(2).listOwned() }

    private fun copy(id: String, doc: String, created: Long = 1_700_000_000_000L) {
        drive.files[id] = RemoteFile(id, doc, ByteArray(7), created)
    }

    @Test
    fun listsEveryPageWithDocumentIdSizeAndCreationTime() {
        (1..5).forEach { copy("f$it", "doc$it", 1_700_000_000_000L + it) }
        val listing = list() as CloudListing.Complete
        assertEquals(5, listing.files.size)
        val first = listing.files.first { it.fileId == "f1" }
        assertEquals("doc1", first.documentId)
        assertEquals(7L, first.sizeBytes)
        assertEquals(1_700_000_000_000L + 1, first.createdAt)
    }

    @Test
    fun anEmptyFolderIsACompleteEmptyListing() {
        assertEquals(CloudListing.Complete(emptyList()), list())
    }

    @Test
    fun anUnsafeDocumentIdIsNeverReported() {
        copy("f1", "../evil")
        copy("f2", "good")
        assertEquals(listOf("good"), (list() as CloudListing.Complete).files.map { it.documentId })
    }

    @Test
    fun failuresNeverLookLikeAnEmptyOrPartialListing() {
        copy("f1", "doc1")
        drive.failAllWith = 403
        drive.failAllBody = """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""
        assertEquals(CloudListing.NeedsConsent, list())
        drive.failAllWith = 429
        assertEquals(CloudListing.Failed, list())
        drive.failAllWith = 401
        assertEquals(CloudListing.Failed, list())
        assertEquals(1, auth.invalidated)
        drive.failAllWith = null
        auth.token = com.paperly.app.domain.auth.DriveToken.Unavailable
        assertTrue(list() is CloudListing.Failed)
    }
}
