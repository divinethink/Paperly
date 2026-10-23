package com.paperly.app.data.sync

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class ReadingTestAuth(initial: AuthState) : AuthRepository {
    override val state: Flow<AuthState> = MutableStateFlow(initial)

    override suspend fun signIn(activity: Context) = SignInResult.FAILED

    override suspend fun signOut(activity: Context) = Unit
}

private class ReadingTestFiles(private val dir: File) : DocumentFileStore {
    override suspend fun store(source: Uri, documentId: String) = throw UnsupportedOperationException()

    override suspend fun storeFrom(input: InputStream, documentId: String): StoredFile =
        throw UnsupportedOperationException()

    override fun resolve(documentId: String): File? = File(dir, documentId).takeIf { it.isFile }

    override suspend fun delete(documentId: String) = File(dir, documentId).delete()
}

/** Any call here means the new processor touched the file cloud, which it must never do for documents. */
private class ReadingTestRemoteFiles : RemoteFileStore {
    var calls = 0

    override suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult {
        calls++
        return FileSyncResult.Done("x")
    }

    override suspend fun download(documentId: String, sha256: String, target: DownloadTarget): FileSyncResult {
        calls++
        return FileSyncResult.NotFound
    }

    override suspend fun forgetUploads() {
        calls++
    }

    override suspend fun delete(documentId: String): RemoteResult {
        calls++
        return RemoteResult.OK
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingSyncProcessorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase
    private lateinit var settings: DataStore<Preferences>
    private lateinit var filesDir: File
    private val remoteReading = FakeReadingRemote()
    private val remoteFiles = ReadingTestRemoteFiles()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        settings = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined + Job())) {
            File(tmp.newFolder(), "s.preferences_pb")
        }
        filesDir = tmp.newFolder()
    }

    @After
    fun tearDown() = db.close()

    private fun processor(auth: AuthState = AuthState.SignedIn("u1", null)) = ReadingSyncProcessor(
        db.syncItemDao(),
        ReadingTestAuth(auth),
        settings,
        ReadingPushStep(db.readerDao(), remoteReading),
        ReadingPullStep(db.documentDao(), db.readerDao(), db.syncItemDao(), remoteReading, settings),
        DownloadStep(db.cloudSyncDao(), ReadingTestFiles(filesDir), remoteFiles),
    )

    private fun doc(id: String) = DocumentEntity(
        documentId = id,
        title = "T-$id",
        type = "pdf",
        sizeBytes = 1,
        checksum = "c-$id",
        createdAt = 1,
        updatedAt = 2,
    )

    private suspend fun waiting() = db.syncItemDao().getDue(SyncItemState.FAILED, Long.MAX_VALUE, 100).size

    @Test
    fun signedOutLeavesTheQueueUntouched() = runBlocking {
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.PUT, 10L)
        assertEquals(SyncRunResult.NOT_SIGNED_IN, processor(AuthState.SignedOut).run { 100L })
        assertEquals(1, waiting())
    }

    @Test
    fun pausedLeavesTheQueueUntouched() = runBlocking {
        settings.edit { it[booleanPreferencesKey("sync_paused")] = true }
        db.syncItemDao().enqueue(SyncEntityType.READING, "d1", SyncOperation.PUT, 10L)
        assertEquals(SyncRunResult.PAUSED, processor().run { 100L })
        assertEquals(1, waiting())
    }

    @Test
    fun firstSignInSendsEveryStoredReadingPositionAndNothingElse() = runBlocking {
        db.documentDao().insert(doc("d1"))
        db.readerDao().upsertState(ReadingStateEntity("d1", "3", 0.1f, 5L))
        assertEquals(SyncRunResult.DONE, processor().run { 100L })
        assertEquals(listOf("d1"), remoteReading.puts.map { it.documentId })
        assertEquals(0, remoteFiles.calls)
        assertEquals(0, waiting())
    }

    @Test
    fun oldDocumentAndFileItemsAreFinishedWithoutAnyCloudCall() = runBlocking {
        db.syncItemDao().enqueue(SyncEntityType.DOCUMENT, "d1", SyncOperation.PUT, 10L)
        db.syncItemDao().enqueue(SyncEntityType.FILE, "d1", SyncOperation.PUT, 10L)
        db.syncItemDao().enqueue(SyncEntityType.FILE, "d2", SyncOperation.DELETE, 10L)
        assertEquals(SyncRunResult.DONE, processor().run { 100L })
        assertEquals(0, waiting())
        assertEquals(0, remoteFiles.calls)
        assertTrue(remoteReading.puts.isEmpty())
    }

    @Test
    fun aNewerCloudPositionIsAppliedToABookThatIsHere() = runBlocking {
        db.documentDao().insert(doc("d1"))
        remoteReading.stored["d1"] = ReadingMeta("d1", "9", 0.9f, 500L)
        assertEquals(SyncRunResult.DONE, processor().run { 1_000L })
        assertEquals("9", db.readerDao().getState("d1")?.locator)
        assertEquals(0, waiting()) // applying a pulled position never queues it back
    }

    @Test
    fun aPositionForABookThatIsNotHereIsKeptInTheCloudAndIgnored() = runBlocking {
        remoteReading.stored["gone"] = ReadingMeta("gone", "9", 0.9f, 500L)
        assertEquals(SyncRunResult.DONE, processor().run { 1_000L })
        assertEquals(null, db.readerDao().getState("gone"))
        assertEquals(0, remoteFiles.calls)
    }

    @Test
    fun aTransientFailureBacksOffAndAsksForAnotherRun() = runBlocking {
        db.documentDao().insert(doc("d1"))
        db.readerDao().upsertState(ReadingStateEntity("d1", "3", 0.1f, 5L))
        remoteReading.result = RemoteResult.RETRY
        assertEquals(SyncRunResult.WAIT, processor().run { 100L })
        assertEquals(1, waiting())
        remoteReading.result = RemoteResult.OK
        assertEquals(SyncRunResult.DONE, processor().run { Long.MAX_VALUE / 2 })
        assertEquals(0, waiting())
    }
}
