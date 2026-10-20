package com.paperly.app.data.privacy

import android.net.Uri
import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.SyncItemEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.data.scanner.ScanSourceStore
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.privacy.ScopedDeleteOutcome
import java.io.File
import java.io.InputStream
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
class LocalTypeWipeTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var db: PaperlyDatabase
    private val dir = File(context.filesDir, "wipe-type-${System.nanoTime()}").apply { mkdirs() }

    private val files = object : DocumentFileStore {
        override suspend fun store(source: Uri, documentId: String): StoredFile = error("unused")
        override suspend fun storeFrom(input: InputStream, documentId: String): StoredFile = error("unused")
        override fun resolve(documentId: String): File? = File(dir, documentId).takeIf { it.exists() }
        override suspend fun delete(documentId: String) = File(dir, documentId).delete()
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaperlyDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun seed(id: String, type: String, trashed: Boolean = false) = runBlocking {
        db.documentDao().insert(
            DocumentEntity(id, id, type, 1, "c", createdAt = 1, updatedAt = 1, deletedAt = if (trashed) 5L else null),
        )
        File(dir, id).writeText("x")
        val item = SyncItemEntity("s-$id", "document", id, "upload", "queued", createdAt = 1, updatedAt = 1)
        db.syncItemDao().insertIgnore(item)
    }

    private fun wipe(type: LibraryType) = runBlocking {
        val impl = LocalTypeWipeImpl(db.aggregateDao(), db.trashDao(), db.syncItemDao(), files, ScanSourceStore(context))
        impl.wipe(type)
    }

    @Test
    fun `wiping PDF keeps EPUB and trashed documents`() {
        seed("p1", "pdf")
        seed("p2", "pdf")
        seed("e1", "epub")
        seed("p3", "pdf", trashed = true)

        assertEquals(ScopedDeleteOutcome.Done(2), wipe(LibraryType.PDF))

        val left = runBlocking { db.aggregateDao().getAllIds() }.toSet()
        assertEquals(setOf("e1", "p3"), left)
        assertFalse(File(dir, "p1").exists())
        assertTrue(File(dir, "e1").exists())
        assertTrue(File(dir, "p3").exists())
    }

    @Test
    fun `queued sync items of wiped documents are dropped and others kept`() {
        seed("p1", "pdf")
        seed("e1", "epub")
        wipe(LibraryType.PDF)
        assertEquals(0, runBlocking { db.syncItemDao().countItem("s-p1") })
        assertEquals(1, runBlocking { db.syncItemDao().countItem("s-e1") })
    }
}
