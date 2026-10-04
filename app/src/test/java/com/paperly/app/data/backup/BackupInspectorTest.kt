package com.paperly.app.data.backup

import androidx.room.Room
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.domain.backup.InspectResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

/** P9-C: "Check a backup file" / restore preview. Must classify correctly and write nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupInspectorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: PaperlyDatabase
    private lateinit var inspector: BackupInspectorImpl

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, PaperlyDatabase::class.java).allowMainThreadQueries().build()
        inspector = BackupInspectorImpl(app, db.documentDao())
    }

    @After
    fun tearDown() = db.close()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun source(id: String, content: String): BackupSource {
        val bytes = content.toByteArray()
        val file = tmp.newFile(id).apply { writeBytes(bytes) }
        return BackupSource(
            BackupDocument(id, "T $id", "pdf", bytes.size.toLong(), sha(bytes), null, emptyList(), false, 1L, 2L, null),
            file,
        )
    }

    private val extras = BackupExtras(
        bookmarks = listOf(BackupBookmark("b1", "d1", "3", null, 5L)),
        scanPages = listOf(BackupScanPage("d1", 0, "Cover", null, 6L), BackupScanPage("d2", 1, null, "n", 7L)),
    )

    private fun archive(vararg s: BackupSource): ByteArray = ByteArrayOutputStream().also {
        BackupArchive.write(it, s.toList(), listOf(BackupFolder("f1", "Work", null, 5L)), BackupHeader(8, "1.2", 99L), extras)
    }.toByteArray()

    private fun inspect(bytes: ByteArray): InspectResult =
        runBlocking { inspector.inspectStream(ByteArrayInputStream(bytes)) }

    private fun zipOf(manifest: String, files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bo ->
        ZipOutputStream(bo).use { z ->
            files.forEach { (id, b) ->
                z.putNextEntry(ZipEntry("documents/$id"))
                z.write(b)
                z.closeEntry()
            }
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(manifest.toByteArray())
            z.closeEntry()
        }
    }.toByteArray()

    private fun docJson(id: String, bytes: ByteArray) =
        """{"documentId":"$id","title":"t","type":"pdf","sizeBytes":${bytes.size},"checksum":"${sha(bytes)}"}"""

    @Test
    fun healthyBackupGivesAFullPreview() {
        val ok = inspect(archive(source("d1", "one"), source("d2", "two"))) as InspectResult.Ok
        val p = ok.preview
        assertEquals(2, p.documents)
        assertEquals(2, p.newDocuments)
        assertEquals(0, p.alreadyPresent)
        assertEquals(1, p.folders)
        assertEquals(1, p.bookmarks)
        assertEquals(2, p.scanPages)
        assertEquals("1.2", p.appVersion)
        assertEquals(99L, p.createdAt)
        assertEquals(0, p.unreadableEntries)
    }

    @Test
    fun alreadyPresentCountsSameIdEvenInTrashAndSameContent() {
        val s1 = source("d1", "one")
        val s2 = source("d2", "two")
        val s3 = source("d3", "three")
        runBlocking {
            // d1: same content under another id (active) -> present; d2: same id but in Trash -> present
            db.documentDao().insert(DocumentEntity("other", "x", "pdf", 3, s1.doc.checksum, createdAt = 1, updatedAt = 1))
            db.documentDao().insert(DocumentEntity("d2", "x", "pdf", 3, "zz", createdAt = 1, updatedAt = 1, deletedAt = 5L))
        }
        val p = (inspect(archive(s1, s2, s3)) as InspectResult.Ok).preview
        assertEquals(3, p.documents)
        assertEquals(2, p.alreadyPresent)
        assertEquals(1, p.newDocuments)
    }

    @Test
    fun inspectingWritesNothing() {
        inspect(archive(source("d1", "one")))
        assertTrue(runBlocking { db.aggregateDao().getAllIds() }.isEmpty())
        assertTrue(runBlocking { db.folderDao().getAll() }.isEmpty())
    }

    @Test
    fun tamperedEntryIsDamaged() {
        val good = "real".toByteArray()
        val zip = zipOf("""{"formatVersion":1,"documents":[${docJson("d1", good)}]}""", mapOf("d1" to "fake".toByteArray()))
        assertEquals(InspectResult.Damaged(1), inspect(zip))
    }

    @Test
    fun listedDocumentWithoutAnEntryIsDamaged() {
        val zip = zipOf("""{"formatVersion":1,"documents":[${docJson("d1", "x".toByteArray())}]}""", emptyMap())
        assertEquals(InspectResult.Damaged(1), inspect(zip))
    }

    @Test
    fun unreadableManifestEntryCountsAsDamaged() {
        val zip = zipOf("""{"formatVersion":1,"documents":[{"documentId":"../evil","title":"t","type":"pdf"}]}""", emptyMap())
        assertEquals(InspectResult.Damaged(1), inspect(zip))
    }

    @Test
    fun truncatedOrGarbageIsInvalid() {
        val bytes = archive(source("d1", "hello hello hello"))
        assertEquals(InspectResult.Invalid, inspect(bytes.copyOf(bytes.size / 2)))
        assertEquals(InspectResult.Invalid, inspect("not a zip at all".toByteArray()))
        assertEquals(InspectResult.Invalid, inspect(ByteArray(0)))
    }

    @Test
    fun newerFormatIsReportedAsNewerVersion() {
        val zip = zipOf("""{"formatVersion":99,"documents":[]}""", emptyMap())
        assertEquals(InspectResult.NewerVersion, inspect(zip))
    }
}
