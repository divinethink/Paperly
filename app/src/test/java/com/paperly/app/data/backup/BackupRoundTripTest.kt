package com.paperly.app.data.backup

import android.net.Uri
import androidx.room.Room
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.core.database.BookmarkEntity
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.FolderEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.core.database.ScanPageEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.file.StoredFile
import com.paperly.app.domain.backup.RestoreResult
import com.paperly.app.domain.backup.RestoreSummary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private fun sha256(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Plain-directory file store (the real one needs app storage); same contract: never overwrite, report sha-256. */
private class DirFileStore(private val dir: File) : DocumentFileStore {
    override suspend fun store(source: Uri, documentId: String): StoredFile = throw UnsupportedOperationException()

    override suspend fun storeFrom(input: InputStream, documentId: String): StoredFile {
        val target = File(dir, documentId)
        if (target.exists()) throw IOException("exists")
        val bytes = input.readBytes()
        target.writeBytes(bytes)
        return StoredFile(target.path, bytes.size.toLong(), sha256(bytes))
    }

    override fun resolve(documentId: String): File? = File(dir, documentId).takeIf { it.exists() }

    override suspend fun delete(documentId: String): Boolean = File(dir, documentId).delete()
}

/**
 * P9-A: the whole export -> restore chain on a real (in-memory) Room DB, two "phones" (fresh DB + fresh file dir).
 * Compares what the user would see, not just that the ZIP verifies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Phone(val db: PaperlyDatabase, val dir: File, val repo: BackupRepositoryImpl, val extras: BackupExtrasStore)

    private val phones = mutableListOf<Phone>()

    private fun newPhone(name: String): Phone {
        val ctx = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(ctx, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        val dir = tmp.newFolder(name)
        val extras = BackupExtrasStore(db)
        val repo = BackupRepositoryImpl(ctx, db.documentDao(), db.aggregateDao(), db.folderDao(), DirFileStore(dir), extras)
        return Phone(db, dir, repo, extras).also { phones += it }
    }

    @After
    fun tearDown() = phones.forEach { it.db.close() }

    private val pdfBytes = "%PDF-1.4 pdf bytes".toByteArray()
    private val epubBytes = ByteArray(5_000) { (it % 251).toByte() }

    private fun seed(p: Phone) = runBlocking {
        p.db.folderDao().insert(FolderEntity("f1", "Work", null, 10L))
        listOf(
            Triple("d1", pdfBytes, "pdf"),
            Triple("d2", epubBytes, "epub"),
            Triple("gone", "trashed".toByteArray(), "pdf"),
        ).forEach { (id, bytes, type) ->
            File(p.dir, id).writeBytes(bytes)
            p.db.documentDao().insert(
                DocumentEntity(
                    documentId = id, title = "Title $id", type = type, sizeBytes = bytes.size.toLong(),
                    checksum = sha256(bytes), localUri = File(p.dir, id).path,
                    folderId = if (id == "d1") "f1" else null,
                    tags = if (id == "d1") listOf("a", "b") else null,
                    isFavorite = id == "d1", createdAt = 100L, updatedAt = 200L,
                    lastOpenedAt = if (id == "d1") 300L else null,
                    deletedAt = if (id == "gone") 400L else null,
                ),
            )
        }
        p.db.readerDao().upsertState(ReadingStateEntity("d1", "7", 42.5f, 9L))
        p.db.readerDao().insertBookmark(BookmarkEntity("b1", "d1", "3", "Ch", 5L))
        p.db.annotationDao().insert(
            AnnotationEntity("a1", "d1", "2", "highlight", "blue", 0.1f, 0.2f, 0.5f, 0.6f, "note", 6L, 7L),
        )
        p.db.scanPageDao().upsert(ScanPageEntity("d1", 0, "Cover", "front page", 11L))
        p.db.scanPageDao().upsert(ScanPageEntity("d1", 2, null, "only a note", 12L))
    }

    private fun exportBytes(p: Phone): ByteArray = runBlocking {
        val out = ByteArrayOutputStream()
        p.repo.writeArchive(out).also { assertEquals(2 to 0, it) } // trashed document is not exported
        out.toByteArray()
    }

    private fun restore(p: Phone, zip: ByteArray): RestoreResult = runBlocking {
        p.repo.restoreFromFile(tmp.newFile().apply { writeBytes(zip) })
    }

    @Test
    fun exportThenRestoreOnAFreshPhoneKeepsEverything() {
        val a = newPhone("a")
        seed(a)
        val zip = exportBytes(a)
        assertTrue(BackupArchive.verify(ByteArrayInputStream(zip)))

        val b = newPhone("b")
        assertEquals(RestoreResult.Done(RestoreSummary(2, 0, 0)), restore(b, zip))

        runBlocking {
            val d1 = b.db.documentDao().getById("d1")!!
            assertEquals("Title d1", d1.title)
            assertEquals("f1", d1.folderId)
            assertEquals(listOf("a", "b"), d1.tags)
            assertTrue(d1.isFavorite)
            assertEquals(100L, d1.createdAt)
            assertEquals(300L, d1.lastOpenedAt)
            assertArrayEquals(pdfBytes, File(b.dir, "d1").readBytes())
            assertArrayEquals(epubBytes, File(b.dir, "d2").readBytes())
            assertNull(b.db.documentDao().getById("gone"))
            assertEquals(listOf("Work"), b.db.folderDao().getAll().map { it.name })

            val sent = a.extras.collect(setOf("d1", "d2"))
            val got = b.extras.collect(setOf("d1", "d2"))
            assertEquals(sent, got)
            assertEquals(2, got.scanPages.size)
            assertEquals("Cover", got.scanPages.first { it.pageIndex == 0 }.title)
            assertNull(got.scanPages.first { it.pageIndex == 2 }.title)
        }
    }

    @Test
    fun restoringTheSameBackupTwiceChangesNothing() {
        val a = newPhone("a")
        seed(a)
        val zip = exportBytes(a)
        val b = newPhone("b")
        restore(b, zip)
        val before = runBlocking { b.extras.collect(setOf("d1", "d2")) }

        assertEquals(RestoreResult.Done(RestoreSummary(0, 2, 0)), restore(b, zip))
        assertEquals(before, runBlocking { b.extras.collect(setOf("d1", "d2")) })
    }

    @Test
    fun restoreNeverOverwritesALocallyEditedScanPage() {
        val a = newPhone("a")
        seed(a)
        val zip = exportBytes(a)
        val b = newPhone("b")
        restore(b, zip)
        runBlocking { b.db.scanPageDao().upsert(ScanPageEntity("d1", 0, "Edited here", null, 99L)) }

        restore(b, zip)

        val page = runBlocking { b.extras.collect(setOf("d1")).scanPages.first { it.pageIndex == 0 } }
        assertEquals("Edited here", page.title)
    }

    // --- frozen old-format fixtures: these strings must keep restoring, whatever the writer evolves into ---

    private fun zipOf(manifest: String, files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bo ->
        ZipOutputStream(bo).use { z ->
            files.forEach { (id, bytes) ->
                z.putNextEntry(ZipEntry("documents/$id"))
                z.write(bytes)
                z.closeEntry()
            }
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(manifest.toByteArray())
            z.closeEntry()
        }
    }.toByteArray()

    private val legacyBytes = "legacy file".toByteArray()

    private fun legacyDoc() =
        """{"documentId":"old1","title":"Old","type":"pdf","sizeBytes":${legacyBytes.size},"checksum":"${sha256(legacyBytes)}"}"""

    @Test
    fun firstGenerationArchiveWithoutAnyExtrasStillRestores() {
        val zip = zipOf(
            """{"formatVersion":1,"schemaVersion":4,"appVersion":"0.1","createdAt":1,"folders":[],"documents":[${legacyDoc()}]}""",
            mapOf("old1" to legacyBytes),
        )
        val p = newPhone("p")
        assertEquals(RestoreResult.Done(RestoreSummary(1, 0, 0)), restore(p, zip))
        assertNotNull(runBlocking { p.db.documentDao().getById("old1") })
        assertEquals(BackupExtras.EMPTY, runBlocking { p.extras.collect(setOf("old1")) })
    }

    @Test
    fun archiveWithExtrasButWithoutScanPagesStillRestores() {
        val zip = zipOf(
            """{"formatVersion":1,"schemaVersion":7,"folders":[],"documents":[${legacyDoc()}],
              "readingState":[{"documentId":"old1","locator":"4","progressPercent":10.0,"lastOpenedAt":3}],
              "bookmarks":[],"annotations":[]}""",
            mapOf("old1" to legacyBytes),
        )
        val p = newPhone("p")
        assertEquals(RestoreResult.Done(RestoreSummary(1, 0, 0)), restore(p, zip))
        val extras = runBlocking { p.extras.collect(setOf("old1")) }
        assertEquals(1, extras.readingState.size)
        assertTrue(extras.scanPages.isEmpty())
    }

    @Test
    fun badScanPageEntriesAreDroppedNotFatal() {
        val zip = zipOf(
            """{"formatVersion":1,"folders":[],"documents":[${legacyDoc()}],"scanPages":[
              {"documentId":"old1","pageIndex":-1,"title":"neg"},
              {"documentId":"nope","pageIndex":0,"title":"unknown doc"},
              {"documentId":"old1","pageIndex":1},
              {"documentId":"old1","pageIndex":3,"title":"ok","updatedAt":5}]}""",
            mapOf("old1" to legacyBytes),
        )
        val p = newPhone("p")
        assertEquals(RestoreResult.Done(RestoreSummary(1, 0, 0)), restore(p, zip))
        val pages = runBlocking { p.extras.collect(setOf("old1")).scanPages }
        assertEquals(listOf(3), pages.map { it.pageIndex })
    }

    @Test
    fun entryThatDoesNotMatchItsChecksumIsRejectedAndLeavesNoFile() {
        val zip = zipOf(
            """{"formatVersion":1,"folders":[],"documents":[${legacyDoc()}]}""",
            mapOf("old1" to "tampered bytes!".toByteArray()),
        )
        val p = newPhone("p")
        assertEquals(RestoreResult.Done(RestoreSummary(0, 0, 1)), restore(p, zip))
        assertNull(runBlocking { p.db.documentDao().getById("old1") })
        assertTrue(!File(p.dir, "old1").exists())
    }

    @Test
    fun newerFormatIsRefusedBeforeAnythingIsWritten() {
        val zip = zipOf("""{"formatVersion":99,"folders":[],"documents":[${legacyDoc()}]}""", mapOf("old1" to legacyBytes))
        val p = newPhone("p")
        assertEquals(RestoreResult.NewerVersion, restore(p, zip))
        assertNull(runBlocking { p.db.documentDao().getById("old1") })
    }
}
