package com.paperly.app.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteResult
import com.paperly.app.domain.sync.SyncBackoff
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    override suspend fun putDocument(uid: String, meta: DocumentMeta): RemoteResult {
        if (result == RemoteResult.OK) puts += meta
        return result
    }

    override suspend fun deleteDocument(uid: String, documentId: String): RemoteResult {
        if (result == RemoteResult.OK) deletes += documentId
        return result
    }

    override suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>? = emptyList()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncProcessorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase
    private val auth = FakeAuth(AuthState.SignedIn("u1", null))
    private val remote = FakeRemote()
    private lateinit var processor: SyncProcessor

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Unconfined + Job())) {
            File(tmp.newFolder(), "s.preferences_pb")
        }
        processor = SyncProcessor(db.syncItemDao(), db.documentDao(), remote, auth, store)
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

    private suspend fun enqueue(id: String, op: String = SyncOperation.PUT, now: Long = 10L) =
        db.syncItemDao().enqueue(SyncEntityType.DOCUMENT, id, op, now)

    private suspend fun waiting() = db.syncItemDao().countWaiting(SyncItemState.FAILED)

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
        val item = db.syncItemDao().getDue(SyncItemState.FAILED, 100L + SyncBackoff.delayMs(1), 10).single()
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
}
