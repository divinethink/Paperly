package com.paperly.app.data.scanner

import androidx.room.Room
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.core.database.BookmarkEntity
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.database.ScanPageEditEntity
import com.paperly.app.core.database.ScanPageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S4: the page-remap transaction moves every page-keyed row together, and rolls back when the file step fails. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanPageRemapDaoTest {
    private lateinit var db: PaperlyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.documentDao().insert(
                DocumentEntity(
                    documentId = "d1", title = "Scan", type = "scanned-pdf", sizeBytes = 1L, checksum = "c",
                    localUri = "x", createdAt = 1L, updatedAt = 1L,
                ),
            )
            db.scanPageDao().upsert(ScanPageEntity("d1", 0, "Zero", null, 1L))
            db.scanPageDao().upsert(ScanPageEntity("d1", 1, "One", "n1", 1L))
            db.scanPageDao().upsert(ScanPageEntity("d1", 2, "Two", null, 1L))
            db.annotationDao().insert(
                AnnotationEntity("a2", "d1", "2", "highlight", null, 0f, 0f, 1f, 1f, null, 1L, 1L),
            )
            db.readerDao().insertBookmark(BookmarkEntity("b0", "d1", "0", null, 1L))
            db.readerDao().insertBookmark(BookmarkEntity("b1", "d1", "1", null, 1L))
            db.readerDao().upsertState(ReadingStateEntity("d1", "2", 50f, 1L))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun rows(n: Int) = List(n) { ScanPageEditEntity("d1", it, "p$it.img", 0, null, "ORIGINAL", 5L) }

    private suspend fun titles() =
        db.scanPageRemapDao().pages("d1").sortedBy { it.pageIndex }.map { it.pageIndex to it.title }

    @Test
    fun deletePageZeroShiftsEverythingUp() = runBlocking {
        var fileStep = false
        // old 1 -> 0, old 2 -> 1 ; page 0 deleted
        db.scanPageRemapDao().remap("d1", mapOf(1 to 0, 2 to 1), 2, rows(2)) { fileStep = true }
        assertTrue(fileStep)
        assertEquals(listOf(0 to "One", 1 to "Two"), titles())
        assertEquals("1", db.scanPageRemapDao().annotationLocators("d1").single().locator)
        // page 0's bookmark went with it
        assertEquals(listOf("0"), db.scanPageRemapDao().bookmarks("d1").map { it.locator })
        assertEquals("1", db.scanPageRemapDao().readingState("d1")!!.locator)
        assertEquals(2, db.scanPageEditDao().getForDocument("d1").size)
    }

    @Test
    fun reorderKeepsAllRows() = runBlocking {
        db.scanPageRemapDao().remap("d1", mapOf(0 to 2, 1 to 0, 2 to 1), 3, rows(3)) {}
        assertEquals(listOf(0 to "One", 1 to "Two", 2 to "Zero"), titles())
        assertEquals("1", db.scanPageRemapDao().annotationLocators("d1").single().locator)
        assertEquals(setOf("2", "0"), db.scanPageRemapDao().bookmarks("d1").map { it.locator }.toSet())
    }

    @Test
    fun failingFileStepRollsEveryRowBack() = runBlocking {
        try {
            db.scanPageRemapDao().remap("d1", mapOf(1 to 0, 2 to 1), 2, rows(2)) { error("move failed") }
            fail("expected the failure to propagate")
        } catch (expected: IllegalStateException) {
            // rolled back below
        }
        assertEquals(listOf(0 to "Zero", 1 to "One", 2 to "Two"), titles())
        assertEquals("2", db.scanPageRemapDao().annotationLocators("d1").single().locator)
        assertEquals("2", db.scanPageRemapDao().readingState("d1")!!.locator)
        assertEquals(2, db.scanPageRemapDao().bookmarks("d1").size)
    }

    @Test
    fun highlightOnARemovedPageAbortsTheWholeChange() = runBlocking {
        try {
            db.scanPageRemapDao().remap("d1", mapOf(0 to 0, 1 to 1), 2, rows(2)) {} // page 2 (highlighted) dropped
            fail("expected abort")
        } catch (expected: IllegalStateException) {
            // rolled back below
        }
        assertEquals(3, db.scanPageRemapDao().pages("d1").size)
        assertEquals("2", db.scanPageRemapDao().annotationLocators("d1").single().locator)
    }
}
