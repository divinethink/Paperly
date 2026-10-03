package com.paperly.app.data.sync

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.database.SyncBaseEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.core.model.SYNC_ERROR_CONFLICT
import com.paperly.app.core.model.StorageState
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.ConflictCheck
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.RemoteDocument
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteResult
import com.paperly.app.domain.sync.SyncBackoff
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class FakeAuth(initial: AuthState) : AuthRepository {
    val current = MutableStateFlow(initial)
    override val state: Flow<AuthState> = current

    override suspend fun signIn(activity: Context) = SignInResult.FAILED

    override suspend fun signOut(activity: Context) = Unit
}

private class FakeRemote(var result: RemoteResult = RemoteResult.OK) : RemoteMetadataStore {
    val puts = mutableListOf<DocumentMeta>()
    val deletes = mutableListOf<String>()

    /** Stands in for Firestore: documentId -> cloud updatedAt, checked with the real [ConflictCheck]. */
    val cloud = mutableMapOf<String, Long>()
    val basesSeen = mutableListOf<Long?>()

    override suspend fun putDocument(uid: String, meta: DocumentMeta, baseUpdatedAt: Long?): RemoteResult {
        if (result != RemoteResult.OK) return result
        basesSeen += baseUpdatedAt
        if (!ConflictCheck.canWrite(baseUpdatedAt, cloud[meta.documentId], meta.updatedAt)) return RemoteResult.CONFLICT
        cloud[meta.documentId] = meta.updatedAt
        puts += meta
        return RemoteResult.OK
    }

    override suspend fun deleteDocument(uid: String, documentId: String): RemoteResult {
        if (result == RemoteResult.OK) deletes += documentId
        return result
    }

    override suspend fun fetchDocument(uid: String, documentId: String) = RemoteDocument.Absent

    override suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>? = emptyList()
}

private class FakeFiles(private val dir: File) : DocumentFileStore {
    override suspend fun store(source: Uri, documentId: String) = throw UnsupportedOperationException()

    override suspend fun storeFrom(input: InputStream, documentId: String): StoredFile =
        throw UnsupportedOperationException()

    override fun resolve(documentId: String): File? = File(dir, documentId).takeIf { it.isFile }

    override suspend fun delete(documentId: String) = File(dir, documentId).delete()
}

private class FakeRemoteFiles(var result: FileSyncResult = FileSyncResult.Done("drive-1")) : RemoteFileStore {
    val uploads = mutableListOf<String>()
    var forgotten = 0

    override suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult {
        uploads += documentId
        return result
    }

    override suspend fun download(documentId: String, sha256: String, target: DownloadTarget) =
        FileSyncResult.NotFound

    override suspend fun forgetUploads() {
        forgotten++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncProcessorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase
    private val auth = FakeAuth(AuthState.SignedIn("u1", null))
    private val remote = FakeRemote()
    private val remoteFiles = FakeRemoteFiles()
    private val remoteReading = FakeReadingRemote()
    private lateinit var processor: SyncProcessor
    private lateinit var filesDir: File
    private lateinit var settings: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        settings = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined + Job())) {
            File(tmp.newFolder(), "s.preferences_pb")
        }
        filesDir = tmp.newFolder()
        val step = FileSyncStep(db.documentDao(), db.cloudSyncDao(), FakeFiles(filesDir), remoteFiles)
        val pull = PullStep(db.documentDao(), db.syncItemDao(), db.cloudSyncDao(), remote, settings)
        val downloads = DownloadStep(db.cloudSyncDao(), FakeFiles(filesDir), remoteFiles)
        val reading = ReadingPushStep(db.readerDao(), remoteReading)
        val readingPull = ReadingPullStep(db.documentDao(), db.readerDao(), db.syncItemDao(), remoteReading, settings)
        processor = SyncProcessor(
            db.syncItemDao(),
            db.documentDao(),
            remote,
            auth,
            settings,
            step,
            pull,
            downloads,
            reading,
            readingPull,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun doc(id: String) = DocumentEntity(
        documentId = id,
        title = "T-$id",
        type = "pdf",
        sizeBytes = 1,
        checksum = "c-$id",
        createdAt = 1,
        updatedAt = 2,
    )

    /** Marks u1 as the account this device already synced with (otherwise the first run resets all bases). */
    private suspend fun knownAccount() = settings.edit { it[stringPreferencesKey("sync_last_uid")] = "u1" }

    private suspend fun enqueue(id: String, op: String = SyncOperation.PUT, now: Long = 10L) =
        db.syncItemDao().enqueue(SyncEntityType.DOCUMENT, id, op, now)

    private suspend fun waiting() = db.syncItemDao().getDue(SyncItemState.FAILED, Long.MAX_VALUE, 100).size

    @Test
    fun enqueueingTwiceKeepsOneRowAndLatestOperationWins() = runBlocking {
        enqueue("d1", SyncOperation.PUT, 10L)
        enqueue("d1", SyncOperation.DELETE, 10L) // same millisecond: version must still increase
        val items = db.syncItemDao().getDue(SyncItemState.FAILED, 100L, 10)
        assertEquals(1, items.size)
        assertEquals(SyncOperation.DELETE, items[0].operation)
        assertTrue(items[0].updatedAt > 10L)
    }

    @Test
    fun staleCompletionNeverDropsAChangeMadeDuringTheRun() = runBlocking {
        val dao = db.syncItemDao()
        enqueue("d1")
        val read = dao.getDue(SyncItemState.FAILED, 100L, 10).single()
        assertEquals(1, dao.claim(read.syncId, read.updatedAt, SyncItemState.RUNNING, SyncItemState.FAILED))
        enqueue("d1", now = 20L) // the owner edits the document while the worker is uploading
        assertEquals(0, dao.complete(read.syncId, read.updatedAt))
        assertEquals(SyncItemState.QUEUED, dao.getDue(SyncItemState.FAILED, 100L, 10).single().state)
    }

    @Test
    fun successfulPutRemembersTheCloudVersionItWrote() = runBlocking {
        db.documentDao().insert(doc("d1")) // updatedAt = 2
        enqueue("d1")
        processor.run { 100L }
        assertEquals(2L, db.syncItemDao().getBase("d1"))
        assertEquals(listOf<Long?>(null), remote.basesSeen) // first write: nothing seen before
    }

    @Test
    fun changedCloudCopyStopsTheItemAsConflictAndKeepsBothSides() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1"))
        db.syncItemDao().setBase(SyncBaseEntity("d1", 1L)) // we last saw cloud version 1
        remote.cloud["d1"] = 5L // another device has since written version 5
        enqueue("d1")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        val item = db.syncItemDao().getDue("none", Long.MAX_VALUE, 10).single { it.entityType == SyncEntityType.DOCUMENT }
        assertEquals(SyncItemState.FAILED, item.state)
        assertEquals(SYNC_ERROR_CONFLICT, item.lastError)
        assertEquals(5L, remote.cloud["d1"]) // cloud untouched
        assertEquals(1L, db.syncItemDao().getBase("d1")) // base untouched
        assertEquals("T-d1", db.documentDao().getById("d1")?.title) // local untouched
    }

    @Test
    fun conflictItemIsNotRetriedByLaterRuns() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1"))
        db.syncItemDao().setBase(SyncBaseEntity("d1", 1L))
        remote.cloud["d1"] = 5L
        enqueue("d1")
        processor.run { 100L }
        remote.basesSeen.clear()
        processor.run { 200L }
        assertTrue(remote.basesSeen.isEmpty())
    }

    @Test
    fun retryAfterACrashBetweenCloudWriteAndBookkeepingIsNotAConflict() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1")) // updatedAt = 2
        db.syncItemDao().setBase(SyncBaseEntity("d1", 1L))
        remote.cloud["d1"] = 2L // our write landed, the base was never updated
        enqueue("d1")
        processor.run { 100L }
        assertEquals(2L, db.syncItemDao().getBase("d1"))
        assertEquals(0, waiting())
    }

    @Test
    fun permanentDeleteForgetsTheBase() = runBlocking {
        knownAccount()
        db.syncItemDao().setBase(SyncBaseEntity("d1", 2L))
        enqueue("d1", SyncOperation.DELETE)
        processor.run { 100L }
        assertEquals(null, db.syncItemDao().getBase("d1"))
    }

    @Test
    fun newAccountStartsWithoutOldBases() = runBlocking {
        db.syncItemDao().setBase(SyncBaseEntity("d1", 2L))
        processor.run { 100L } // first run for this account
        assertEquals(null, db.syncItemDao().getBase("d1"))
    }

    @Test
    fun putSendsMetadataAndClearsTheQueue() = runBlocking {
        db.documentDao().insert(doc("d1"))
        enqueue("d1")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("d1"), remote.puts.map { it.documentId })
        assertEquals(0, waiting())
    }

    @Test
    fun putOfAMissingRowBecomesARemoteDelete() = runBlocking {
        enqueue("gone")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("gone"), remote.deletes)
        assertEquals(0, waiting())
    }

    @Test
    fun firstSignInQueuesTheWholeExistingLibrary() = runBlocking {
        db.documentDao().insert(doc("a"))
        db.documentDao().insert(doc("b")) // inserted without ever being enqueued (pre-v7 library)
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(setOf("a", "b"), remote.puts.map { it.documentId }.toSet())
    }

    @Test
    fun transientFailureBacksOffAndStopsTheRun() = runBlocking {
        db.documentDao().insert(doc("d1"))
        enqueue("d1")
        remote.result = RemoteResult.RETRY
        assertEquals(SyncRunResult.WAIT, processor.run { 100L })
        val dueAt = 100L + SyncBackoff.delayMs(1)
        val item = db.syncItemDao().getDue(SyncItemState.FAILED, dueAt, 10).single { it.entityType == SyncEntityType.DOCUMENT }
        assertEquals(SyncItemState.RETRYING, item.state)
        assertEquals(1, item.attempts)
        assertEquals(100L + SyncBackoff.delayMs(1), item.nextRetryAt)
        // not due yet: nothing is sent, it keeps waiting
        remote.result = RemoteResult.OK
        assertEquals(SyncRunResult.WAIT, processor.run { 101L })
        assertTrue(remote.puts.isEmpty())
        // once due it goes through
        assertEquals(SyncRunResult.DONE, processor.run { 100L + SyncBackoff.delayMs(1) })
        assertEquals(1, remote.puts.size)
    }

    @Test
    fun rejectedItemFailsForGoodUntilTheDocumentChangesAgain() = runBlocking {
        db.documentDao().insert(doc("d1"))
        enqueue("d1")
        remote.result = RemoteResult.DENIED
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(0, waiting()) // FAILED is not "waiting"
        remote.result = RemoteResult.OK
        assertEquals(SyncRunResult.DONE, processor.run { 200L })
        assertTrue(remote.puts.isEmpty())
        enqueue("d1", now = 300L) // a new change re-arms it
        assertEquals(SyncRunResult.DONE, processor.run { 400L })
        assertEquals(1, remote.puts.size)
    }

    @Test
    fun signedOutLeavesTheQueueUntouched() = runBlocking {
        db.documentDao().insert(doc("d1"))
        enqueue("d1")
        auth.current.value = AuthState.SignedOut
        assertEquals(SyncRunResult.NOT_SIGNED_IN, processor.run { 100L })
        assertEquals(1, waiting())
        assertNotNull(db.syncItemDao().getDue(SyncItemState.FAILED, 100L, 10).singleOrNull())
        assertTrue(remote.puts.isEmpty())
    }

    private fun withFile(id: String) {
        File(filesDir, id).writeBytes(byteArrayOf(1, 2, 3))
    }

    private suspend fun storageOf(id: String) = db.documentDao().getById(id)!!

    @Test
    fun documentFileIsUploadedAndTheCloudRefRecorded() = runBlocking {
        db.documentDao().insert(doc("d1"))
        withFile("d1")
        enqueue("d1")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("d1"), remoteFiles.uploads)
        assertEquals("drive-1", storageOf("d1").cloudRef)
        assertEquals(StorageState.SYNCED, storageOf("d1").storageState)
        assertEquals(0, waiting())
        // a later rename must not upload the file again
        enqueue("d1", now = 200L)
        processor.run { 300L }
        assertEquals(1, remoteFiles.uploads.size)
    }

    @Test
    fun trashedDocumentIsNotUploadedUntilRestored() = runBlocking {
        db.documentDao().insert(doc("d1").copy(deletedAt = 5L))
        withFile("d1")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertTrue(remoteFiles.uploads.isEmpty())
    }

    @Test
    fun missingLocalFileFailsThatItemOnly() = runBlocking {
        db.documentDao().insert(doc("nofile"))
        db.documentDao().insert(doc("ok"))
        withFile("ok")
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("ok"), remoteFiles.uploads)
        assertEquals(0, waiting())
    }

    @Test
    fun transientFileFailureBacksOffWithoutLosingTheFile() = runBlocking {
        db.documentDao().insert(doc("d1"))
        withFile("d1")
        remoteFiles.result = FileSyncResult.Retry
        assertEquals(SyncRunResult.WAIT, processor.run { 100L })
        assertEquals(null, storageOf("d1").cloudRef)
        assertEquals(StorageState.RETRYING, storageOf("d1").storageState)
        remoteFiles.result = FileSyncResult.Done("drive-9")
        assertEquals(SyncRunResult.DONE, processor.run { 100L + SyncBackoff.delayMs(1) })
        assertEquals("drive-9", storageOf("d1").cloudRef)
    }

    @Test
    fun missingDriveAccessParksTheFileWithoutCountingAFailure() = runBlocking {
        db.documentDao().insert(doc("d1"))
        db.documentDao().insert(doc("d2"))
        withFile("d1")
        withFile("d2")
        remoteFiles.result = FileSyncResult.NeedsConsent
        processor.run { 100L }
        val parked = db.syncItemDao().getDue(SyncItemState.FAILED, Long.MAX_VALUE, 10)
            .filter { it.entityType == SyncEntityType.FILE }
        assertEquals(2, parked.size) // the run did not stop after the first one
        assertTrue(parked.all { it.attempts == 0 && it.state == SyncItemState.RETRYING })
        // allowing access releases them
        db.cloudSyncDao().releaseDeferred(SyncEntityType.FILE, SyncItemState.RETRYING, SYNC_ERROR_NEEDS_ACCESS)
        remoteFiles.result = FileSyncResult.Done("drive-1")
        assertEquals(SyncRunResult.DONE, processor.run { 101L })
        assertEquals(setOf("d1", "d2"), remoteFiles.uploads.toSet())
    }

    @Test
    fun pausedSyncLeavesTheQueueUntouched() = runBlocking {
        db.documentDao().insert(doc("d1"))
        withFile("d1")
        enqueue("d1")
        settings.edit { it[SyncPrefs.PAUSED] = true }
        assertEquals(SyncRunResult.PAUSED, processor.run { 100L })
        assertTrue(remote.puts.isEmpty() && remoteFiles.uploads.isEmpty())
        assertEquals(1, waiting())
        settings.edit { it[SyncPrefs.PAUSED] = false }
        assertEquals(SyncRunResult.DONE, processor.run { 200L })
        assertEquals(listOf("d1"), remoteFiles.uploads)
    }

    @Test
    fun anotherAccountForgetsTheFirstAccountsCloudCopies() = runBlocking {
        db.documentDao().insert(doc("d1"))
        withFile("d1")
        processor.run { 100L }
        assertEquals("drive-1", storageOf("d1").cloudRef)
        val forgottenBefore = remoteFiles.forgotten
        auth.current.value = AuthState.SignedIn("u2", null)
        remoteFiles.result = FileSyncResult.Done("drive-2")
        processor.run { 200L }
        assertEquals(forgottenBefore + 1, remoteFiles.forgotten)
        assertEquals("drive-2", storageOf("d1").cloudRef)
    }

    private val epubLocator = "{\"href\":\"c1.xhtml\",\"type\":\"application/xhtml+xml\"," +
        "\"locations\":{\"progression\":0.5},\"text\":{\"highlight\":\"secret words\"}}"

    @Test
    fun readingPositionIsPushedWithoutBookText() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1"))
        db.readerDao().upsertState(ReadingStateEntity("d1", epubLocator, 0.5f, 77L))
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.PUT, 10L)
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        val sent = remoteReading.puts.single()
        assertEquals(77L, sent.updatedAt)
        assertFalse(sent.locator.contains("secret"))
        assertEquals(0, waiting())
    }

    @Test
    fun runPullsANewerCloudPositionForAnExistingDocument() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1"))
        remoteReading.stored["d1"] = ReadingMeta("d1", "9", 0.9f, 500L)
        assertEquals(SyncRunResult.DONE, processor.run { 1_000L })
        assertEquals("9", db.readerDao().getState("d1")?.locator)
        assertEquals(0, waiting()) // applying a pulled position never queues it back
    }

    @Test
    fun firstSignInAlsoQueuesStoredReadingPositions() = runBlocking {
        db.documentDao().insert(doc("d1"))
        db.readerDao().upsertState(ReadingStateEntity("d1", "3", 0.1f, 5L)) // saved before sync existed
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("d1"), remoteReading.puts.map { it.documentId })
    }

    @Test
    fun permanentDeleteItemRemovesTheRemotePosition() = runBlocking {
        knownAccount()
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.DELETE, 10L)
        assertEquals(SyncRunResult.DONE, processor.run { 100L })
        assertEquals(listOf("d1"), remoteReading.deletes)
        assertEquals(0, waiting())
    }

    @Test
    fun transientReadingFailureBacksOffLikeAnyOtherItem() = runBlocking {
        knownAccount()
        db.documentDao().insert(doc("d1"))
        db.readerDao().upsertState(ReadingStateEntity("d1", "3", 0.1f, 5L))
        remoteReading.result = RemoteResult.RETRY
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.PUT, 10L)
        assertEquals(SyncRunResult.WAIT, processor.run { 100L })
        val item = db.syncItemDao().getDue(SyncItemState.FAILED, Long.MAX_VALUE, 10)
            .single { it.entityType == SyncEntityType.READING }
        assertEquals(SyncItemState.RETRYING, item.state)
    }
}
