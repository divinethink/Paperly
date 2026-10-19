package com.paperly.app.core.database

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Nested-folder deletes must never lose a document: contents move up, or the folders go and documents stay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderNestingDaoTest {
    private lateinit var db: PaperlyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        val folders = db.folderDao()
        folders.insert(FolderEntity("bills", "Bills", null, 1L))
        folders.insert(FolderEntity("y26", "2026", "bills", 2L))
        folders.insert(FolderEntity("oct", "Oct", "y26", 3L))
        listOf("d-bills" to "bills", "d-y26" to "y26", "d-oct" to "oct", "d-root" to null).forEach { (id, folder) ->
            db.documentDao().insert(
                DocumentEntity(id, id, "scanned-pdf", 1L, "sum-$id", folderId = folder, createdAt = 1L, updatedAt = 1L),
            )
        }
    }

    private suspend fun folderOf(id: String) = db.documentDao().getById(id)?.folderId

    @Test fun deletingAMiddleFolderMovesItsDocumentsAndSubfoldersUpOneLevel() = runBlocking {
        seed()
        db.folderDao().deleteAndLift("y26", "bills")

        assertEquals("bills", folderOf("d-y26"))
        assertEquals("oct", folderOf("d-oct"))
        val byId = db.folderDao().getAll().associateBy { it.folderId }
        assertEquals(setOf("bills", "oct"), byId.keys)
        assertEquals("bills", byId.getValue("oct").parentFolderId)
    }

    @Test fun deletingATopLevelFolderLiftsItsContentsToTheTop() = runBlocking {
        seed()
        db.folderDao().deleteAndLift("bills", null)

        assertEquals(null, folderOf("d-bills"))
        assertEquals(null, db.folderDao().getAll().single { it.folderId == "y26" }.parentFolderId)
    }

    @Test fun deletingASubtreeRemovesTheFoldersButKeepsEveryDocument() = runBlocking {
        seed()
        db.folderDao().deleteSubtree(listOf("bills", "y26", "oct"))

        assertEquals(emptyList<FolderEntity>(), db.folderDao().getAll())
        listOf("d-bills", "d-y26", "d-oct", "d-root").forEach { id ->
            assertNotNull(db.documentDao().getById(id))
            assertEquals(null, folderOf(id))
        }
    }
}
