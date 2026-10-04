package com.paperly.app.data.storage

import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.file.DOCUMENTS_DIR
import com.paperly.app.domain.storage.OrphanCleanResult
import com.paperly.app.domain.storage.OrphanScan
import java.io.File
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

/** P9-D manual orphan clean-up on a real in-memory DB and the app's documents folder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalOrphanRepositoryTest {
    private lateinit var db: PaperlyDatabase
    private lateinit var dir: File
    private lateinit var repo: LocalOrphanRepositoryImpl

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        dir = File(app.filesDir, DOCUMENTS_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        repo = LocalOrphanRepositoryImpl(app, db.aggregateDao())
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun file(name: String, ageHours: Long, text: String = "12345") = File(dir, name).apply {
        writeText(text)
        setLastModified(System.currentTimeMillis() - ageHours * 3_600_000L)
    }

    private fun row(id: String, deletedAt: Long? = null) = runBlocking {
        db.documentDao().insert(DocumentEntity(id, "t", "pdf", 5, "c$id", createdAt = 1, updatedAt = 1, deletedAt = deletedAt))
    }

    @Test
    fun onlyOldUnreferencedFilesAndStaleTempAreFoundAndRemoved() {
        row("active")
        row("trashed", deletedAt = 9L)
        val active = file("active", 5)
        val trashed = file("trashed", 5)
        val orphan = file("orphan", 5)
        val tooNew = file("justImported", 0)
        val staleTmp = file("active.tmp", 5)
        File(dir, "sub").mkdirs()

        assertEquals(OrphanScan(2, 10L), runBlocking { repo.scan() })
        assertTrue(orphan.exists()) // scanning deletes nothing

        assertEquals(OrphanCleanResult(2, 0), runBlocking { repo.clean() })
        assertFalse(orphan.exists())
        assertFalse(staleTmp.exists())
        assertTrue(active.exists())
        assertTrue(trashed.exists())
        assertTrue(tooNew.exists())
        assertEquals(OrphanCleanResult(0, 0), runBlocking { repo.clean() })
    }

    @Test
    fun emptyOrMissingFolderIsFine() {
        dir.deleteRecursively()
        assertEquals(OrphanScan(0, 0L), runBlocking { repo.scan() })
    }
}
