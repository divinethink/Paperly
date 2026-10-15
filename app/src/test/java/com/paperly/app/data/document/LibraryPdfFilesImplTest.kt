package com.paperly.app.data.document

import android.net.Uri
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.core.model.StorageState
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.syncIdOf
import com.paperly.app.domain.sync.SyncQueue
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class LibraryPdfFilesImplTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Files(val dir: File) : DocumentFileStore {
        override suspend fun store(source: Uri, documentId: String) = throw UnsupportedOperationException()

        override suspend fun storeFrom(input: InputStream, documentId: String): StoredFile =
            throw UnsupportedOperationException()

        override fun resolve(documentId: String): File? = File(dir, documentId).takeIf { it.isFile }

        override suspend fun delete(documentId: String) = File(dir, documentId).delete()
    }

    private class Queue : SyncQueue {
        val changed = mutableListOf<String>()

        override suspend fun documentChanged(id: String) {
            changed += id
        }

        override suspend fun documentsChanged(ids: List<String>) = Unit

        override suspend fun readingChanged(id: String) = Unit

        override suspend fun readingFlush(id: String) = Unit

        override suspend fun documentDeleted(id: String) = Unit

        override suspend fun allDocumentsChanged() = Unit

        override suspend fun kick() = Unit

        override suspend fun retryFailed() = Unit

        override suspend fun resumeDeferred() = Unit
    }

    private lateinit var db: PaperlyDatabase
    private lateinit var dir: File
    private val queue = Queue()
    private val files by lazy {
        LibraryPdfFilesImpl(db.documentDao(), Files(dir), db.cloudSyncDao(), db.syncItemDao(), queue)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries().build()
        dir = tmp.newFolder()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(id: String, type: String = "pdf", deletedAt: Long? = null, cloudRef: String? = null) {
        File(dir, id).writeText("x")
        db.documentDao().insert(
            DocumentEntity(
                documentId = id, title = id, type = type, sizeBytes = 1, checksum = "old", cloudRef = cloudRef,
                storageState = if (cloudRef != null) StorageState.SYNCED else StorageState.LOCAL_ONLY,
                createdAt = 1, updatedAt = 2, deletedAt = deletedAt,
            ),
        )
    }

    @Test
    fun writableFile_onlyForLiveLibraryPdfs() = runBlocking {
        insert("pdf1")
        insert("scan1", type = "scanned-pdf")
        insert("epub1", type = "epub")
        insert("trash1", deletedAt = 5)
        assertNotNull(files.writableFile("pdf1"))
        assertNotNull(files.writableFile("scan1"))
        assertNull(files.writableFile("epub1"))
        assertNull(files.writableFile("trash1"))
        assertNull(files.writableFile("missing"))
    }

    @Test
    fun replaced_updatesRow_forgetsCloudCopy_andQueuesFileAndMetadata() = runBlocking {
        insert("pdf1", cloudRef = "drive-1")
        files.replaced("pdf1", "newsum", 123)
        val row = db.documentDao().getById("pdf1")!!
        assertEquals("newsum", row.checksum)
        assertEquals(123L, row.sizeBytes)
        assertNull(row.cloudRef)
        assertEquals(StorageState.QUEUED, row.storageState)
        assertTrue(row.updatedAt > 2L)
        assertEquals(1, db.syncItemDao().countItem(syncIdOf(SyncEntityType.FILE, "pdf1")))
        assertEquals(listOf("pdf1"), queue.changed)
    }
}
