package com.paperly.app.data.sync.drive

import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.RemoteResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real HTTP code (resumable protocol, checksum checks, error mapping) against an in-process fake Drive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveFileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var drive: FakeDriveServer
    private val auth = FakeDriveAuth()
    private val sessions = MemorySessions()
    private lateinit var store: DriveFileStore

    @Before
    fun setUp() {
        drive = FakeDriveServer().start()
        store = DriveFileStore(DriveFileApi(drive.endpoints), auth, sessions)
    }

    @After
    fun tearDown() = drive.stop()

    private fun file(size: Int): Pair<File, ByteArray> {
        val bytes = ByteArray(size) { (it * 31 + 7).toByte() }
        return tmp.newFile().also { it.writeBytes(bytes) } to bytes
    }

    private fun upload(id: String, file: File, sha: String) = runBlocking { store.upload(id, file, sha) }

    @Test
    fun uploadsInChunksAndAdoptsTheExistingCopyNextTime() {
        val (f, bytes) = file(BIG)
        val first = upload("doc1", f, sha256Hex(bytes))
        assertTrue(first is FileSyncResult.Done)
        assertTrue(drive.files.values.single().bytes.contentEquals(bytes))
        assertEquals(3, drive.chunkCount)
        assertTrue(sessions.map.isEmpty())
        val second = upload("doc1", f, sha256Hex(bytes))
        assertEquals((first as FileSyncResult.Done).cloudRef, (second as FileSyncResult.Done).cloudRef)
        assertEquals(1, drive.sessionsCreated)
    }

    @Test
    fun interruptedUploadResumesTheSameSession() {
        val (f, bytes) = file(BIG)
        drive.failChunkNumber = 2
        assertTrue(upload("doc2", f, sha256Hex(bytes)) is FileSyncResult.Retry)
        assertTrue(sessions.map.containsKey("doc2"))
        assertTrue(upload("doc2", f, sha256Hex(bytes)) is FileSyncResult.Done)
        assertEquals(1, drive.sessionsCreated)
        assertTrue(drive.files.values.single().bytes.contentEquals(bytes))
    }

    @Test
    fun expiredSessionStartsANewOne() {
        val (f, bytes) = file(5 * 1024 * 1024)
        val url = drive.endpoints.upload + "/files?uploadType=resumable&upload_id=gone"
        sessions.map["doc3"] = UploadSession(url, sha256Hex(bytes), bytes.size.toLong())
        assertTrue(upload("doc3", f, sha256Hex(bytes)) is FileSyncResult.Done)
        assertEquals(1, drive.sessionsCreated)
    }

    @Test
    fun serverConfirmingFewerBytesGetsTheRest() {
        val (f, bytes) = file(BIG)
        drive.acceptOnlyHalfOnce = true
        assertTrue(upload("doc4", f, sha256Hex(bytes)) is FileSyncResult.Done)
        assertTrue(drive.files.values.single().bytes.contentEquals(bytes))
    }

    @Test
    fun remoteHashMismatchRemovesTheCopyAndRetries() {
        val (f, bytes) = file(300_000)
        drive.wrongSha = true
        assertTrue(upload("doc5", f, sha256Hex(bytes)) is FileSyncResult.Retry)
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun md5IsUsedWhenDriveReportsNoSha256() {
        val (f, bytes) = file(300_000)
        drive.omitSha = true
        assertTrue(upload("doc6", f, sha256Hex(bytes)) is FileSyncResult.Done)
        drive.wrongMd5 = true
        assertTrue(upload("doc7", f, sha256Hex(bytes)) is FileSyncResult.Retry)
    }

    @Test
    fun localFileThatDiffersFromItsChecksumIsNeverUploaded() {
        val (f, bytes) = file(300_000)
        assertTrue(upload("doc8", f, "a".repeat(64)) is FileSyncResult.Denied)
        assertTrue(upload("../x", f, sha256Hex(bytes)) is FileSyncResult.Denied)
        assertEquals(0, drive.requests)
    }

    @Test
    fun httpErrorsMapToTheRightOutcome() {
        val (f, bytes) = file(100_000)
        val sha = sha256Hex(bytes)
        drive.failAllWith = 401
        assertTrue(upload("e1", f, sha) is FileSyncResult.Retry)
        assertEquals(1, auth.invalidated)
        drive.failAllWith = 403
        val cases = mapOf(
            "insufficientPermissions" to FileSyncResult.NeedsConsent,
            "rateLimitExceeded" to FileSyncResult.Retry,
            "storageQuotaExceeded" to FileSyncResult.Retry,
            "accessNotConfigured" to FileSyncResult.Retry,
            "forbidden" to FileSyncResult.Denied,
        )
        cases.forEach { (reason, expected) ->
            drive.failAllBody = """{"error":{"errors":[{"reason":"$reason"}]}}"""
            assertEquals(reason, expected, upload("e1", f, sha))
        }
        drive.failAllWith = 400
        assertEquals(FileSyncResult.Denied, upload("e1", f, sha))
        drive.failAllWith = 429
        assertEquals(FileSyncResult.Retry, upload("e1", f, sha))
    }

    @Test
    fun unavailableAuthMeansRetryLater() {
        val (f, bytes) = file(1000)
        auth.token = DriveToken.Unavailable
        assertEquals(FileSyncResult.Retry, upload("c2", f, sha256Hex(bytes)))
    }

    @Test
    fun downloadIsVerifiedAndCorruptionIsDiscarded() {
        val (f, bytes) = file(700_000)
        val sha = sha256Hex(bytes)
        upload("dl1", f, sha)
        val ok = MemoryTarget()
        assertTrue(runBlocking { store.download("dl1", sha, ok) } is FileSyncResult.Done)
        assertTrue(ok.stored!!.contentEquals(bytes))
        drive.wrongSha = true // the fake then also flips a byte of the content it serves
        val bad = MemoryTarget()
        assertEquals(FileSyncResult.Retry, runBlocking { store.download("dl1", sha, bad) })
        assertEquals(1, bad.discarded)
        assertNull(bad.stored)
        assertEquals(FileSyncResult.NotFound, runBlocking { store.download("nope", sha, MemoryTarget()) })
    }

    private fun cloudCopy(id: String, documentId: String) {
        drive.files[id] = RemoteFile(id, documentId, ByteArray(10))
    }

    private fun delete(id: String) = runBlocking { store.delete(id) }

    @Test
    fun deleteRemovesEveryCopyOfThatDocumentOnly() {
        cloudCopy("f1", "docA")
        cloudCopy("f2", "docA") // a stray duplicate
        cloudCopy("f3", "docB")
        sessions.map["docA"] = UploadSession("u", "s", 1)
        assertEquals(RemoteResult.OK, delete("docA"))
        assertEquals(listOf("f3"), drive.files.keys.toList())
        assertTrue(sessions.map.isEmpty())
        assertEquals(RemoteResult.OK, delete("docA")) // idempotent: nothing left is still done
    }

    @Test
    fun deleteOfADocumentThatWasNeverUploadedIsDone() {
        assertEquals(RemoteResult.OK, delete("neverUploaded"))
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun deleteMapsErrorsAndNeverTouchesDriveForABadId() {
        assertEquals(RemoteResult.DENIED, delete("../x"))
        assertEquals(0, drive.requests)
        cloudCopy("f1", "docA")
        drive.failAllWith = 403
        drive.failAllBody = """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""
        assertEquals(RemoteResult.DEFERRED, delete("docA"))
        drive.failAllWith = 429
        assertEquals(RemoteResult.RETRY, delete("docA"))
        drive.failAllWith = null
        auth.token = DriveToken.Unavailable
        assertEquals(RemoteResult.RETRY, delete("docA"))
        assertEquals(1, drive.files.size) // nothing was lost by the failed attempts
    }

    private companion object {
        const val BIG = 9 * 1024 * 1024 + 123
    }
}
