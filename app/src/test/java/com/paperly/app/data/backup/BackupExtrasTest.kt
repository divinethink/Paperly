package com.paperly.app.data.backup

import androidx.room.Room
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.core.database.BookmarkEntity
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupExtrasTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PaperlyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private val checksum = "a".repeat(64)

    private fun doc(id: String) = BackupDocument(id, "T", "pdf", 1L, checksum, null, emptyList(), false, 1L, 2L, null)

    private fun docRow(id: String) = DocumentEntity(
        documentId = id,
        title = "T",
        type = "pdf",
        sizeBytes = 1,
        checksum = id,
        createdAt = 1,
        updatedAt = 2,
    )

    private val extras = BackupExtras(
        readingState = listOf(BackupReadingState("d1", "7", 42.5f, 9L)),
        bookmarks = listOf(BackupBookmark("b1", "d1", "3", "Ch", 5L)),
        annotations = listOf(
            BackupAnnotation("a1", "d1", "2", "highlight", "blue", 0.1f, 0.2f, 0.5f, 0.6f, null, 6L, 7L),
        ),
    )

    @Test
    fun extrasRoundTripThroughManifest() {
        val m = BackupManifest(1, 4, "1.0", 0L, emptyList(), listOf(doc("d1")), extras)
        val back = BackupManifestCodec.decode(BackupManifestCodec.encode(m))!!
        assertEquals(extras, back.extras)
        assertEquals(0, back.invalidExtras)
    }

    @Test
    fun oldManifestWithoutExtrasDecodesEmpty() {
        val json = """{"formatVersion":1,"documents":[
            {"documentId":"d1","title":"t","type":"pdf","sizeBytes":1,"checksum":"$checksum"}]}"""
        val m = BackupManifestCodec.decode(json)!!
        assertEquals(BackupExtras.EMPTY, m.extras)
        assertEquals(0, m.invalidExtras)
    }

    @Test
    fun archiveWithExtrasStillVerifiesAndReads() {
        val bytes = "x".toByteArray()
        val file = tmp.newFile("d1").apply { writeBytes(bytes) }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val d = doc("d1").copy(checksum = sha, sizeBytes = 1L)
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, listOf(BackupSource(d, file)), emptyList(), BackupHeader(4, "1", 0L), extras)
        assertTrue(BackupArchive.verify(out.toByteArray().inputStream()))
        val zipFile: File = tmp.newFile("b.zip").apply { writeBytes(out.toByteArray()) }
        ZipFile(zipFile).use { assertEquals(extras, BackupArchive.readManifest(it)!!.extras) }
    }

    @Test
    fun invalidExtrasAreDroppedAndCounted() {
        val json = """{"formatVersion":1,"documents":[
            {"documentId":"d1","title":"t","type":"pdf","sizeBytes":1,"checksum":"$checksum"}],
          "readingState":[{"documentId":"zzz","locator":"1","progressPercent":1},
                          {"documentId":"d1","locator":"1","progressPercent":10}],
          "bookmarks":[{"bookmarkId":"../x","documentId":"d1","locator":"1"},
                       {"bookmarkId":"b1","documentId":"d1","locator":"1"}],
          "annotations":[
            {"annotationId":"a1","documentId":"d1","locator":"0","type":"scribble",
             "rectLeft":0,"rectTop":0,"rectRight":1,"rectBottom":1},
            {"annotationId":"a2","documentId":"d1","locator":"0","type":"note",
             "rectLeft":0,"rectTop":0,"rectRight":2,"rectBottom":1},
            {"annotationId":"a3","documentId":"d1","locator":"0","type":"underline","color":"neon","future":1,
             "rectLeft":0,"rectTop":0.1,"rectRight":1,"rectBottom":0.2}]}"""
        val m = BackupManifestCodec.decode(json)!!
        assertEquals(listOf("d1"), m.extras.readingState.map { it.documentId })
        assertEquals(listOf("b1"), m.extras.bookmarks.map { it.bookmarkId })
        assertEquals(listOf("a3"), m.extras.annotations.map { it.annotationId })
        assertNull(m.extras.annotations.single().color) // unknown colour -> default
        assertEquals(4, m.invalidExtras)
        assertEquals(0, m.invalidEntries) // extras never make the archive "invalid"
    }

    @Test
    fun restoreIsIdempotentAndNeverOverwrites() = runBlocking {
        val store = BackupExtrasStore(db)
        db.documentDao().insert(docRow("d1"))
        store.restore("d1", extras)
        store.restore("d1", extras) // second run: no duplicates
        val dao = db.backupExtrasDao()
        assertEquals(1, dao.getAllReadingState().size)
        assertEquals(1, dao.getAllBookmarks().size)
        assertEquals(1, dao.getAllAnnotations().size)

        // existing progress is kept, not replaced
        db.readerDao().upsertState(ReadingStateEntity("d1", "99", 1f, 1L))
        store.restore("d1", extras)
        assertEquals("99", dao.getAllReadingState().single().locator)
    }

    @Test
    fun collectOnlyRequestedDocumentsAndRoundTrip() = runBlocking {
        db.documentDao().insert(docRow("d1"))
        db.documentDao().insert(docRow("d2"))
        db.readerDao().upsertState(ReadingStateEntity("d1", "7", 42.5f, 9L))
        db.readerDao().insertBookmark(BookmarkEntity("b1", "d1", "3", null, 5L))
        db.readerDao().insertBookmark(BookmarkEntity("b2", "d2", "3", null, 5L))
        db.annotationDao().insert(AnnotationEntity("a1", "d2", "0", "note", null, 0f, 0f, 1f, 1f, "hi", 1L, 2L))
        val got = BackupExtrasStore(db).collect(setOf("d1"))
        assertEquals(listOf("d1"), got.readingState.map { it.documentId })
        assertEquals(listOf("b1"), got.bookmarks.map { it.bookmarkId })
        assertTrue(got.annotations.isEmpty())
    }
}
