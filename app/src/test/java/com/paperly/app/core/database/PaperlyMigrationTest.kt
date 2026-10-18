package com.paperly.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.paperly.app.domain.scanner.escapeLike
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Guards the riskiest P1 change: v1 -> v2 must keep every document and match Room's expected schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaperlyMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun createV1Database(): File {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V1_DOCUMENTS)
            INDEXES.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO documents (documentId, title, type, sizeBytes, checksum, storageState, " +
                    "isPasswordProtected, isFavorite, isSensitive, createdAt, updatedAt, schemaVersion) " +
                    "VALUES ('d1', 'Old book', 'pdf', 10, 'abc', 'LOCAL_ONLY', 0, 1, 0, 1, 2, 1)",
            )
            db.version = 1
        }
        return file
    }

    @Test
    fun backupIsCreatedAndVerifiedBeforeMigration() {
        val file = createV1Database()
        val backups = tmp.newFolder("backups")

        DatabaseBackup.backupBeforeMigration(file, backups, targetVersion = 4)

        val backup = File(backups, "$TEST_DB.v1.bak")
        assertTrue(backup.exists())
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version)
            db.rawQuery("SELECT COUNT(*) FROM documents", null).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun noBackupWhenDatabaseIsMissingOrAlreadyCurrent() {
        val backups = tmp.newFolder("none")
        DatabaseBackup.backupBeforeMigration(File(tmp.root, "absent.db"), backups, targetVersion = 4)
        assertFalse(backups.listFiles().orEmpty().isNotEmpty())

        val file = createV1Database()
        DatabaseBackup.backupBeforeMigration(file, backups, targetVersion = 1)
        assertFalse(backups.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun migrationKeepsDocumentsAndAddsWorkingFoldersTable() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val doc = db.documentDao().getById("d1")
            assertNotNull(doc)
            assertEquals("Old book", doc?.title)
            assertTrue(doc?.isFavorite == true)

            db.folderDao().insert(FolderEntity("f1", "Work", null, 5L))
            db.documentDao().updateFolder("d1", "f1")
            assertEquals("f1", db.documentDao().getById("d1")?.folderId)
            assertEquals(listOf("Work"), db.folderDao().observeFolders().first().map { it.name })

            db.folderDao().deleteAndUnassign("f1")
            assertEquals(null, db.documentDao().getById("d1")?.folderId)
            assertNotNull(db.documentDao().getById("d1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationAddsWorkingReaderTablesAndCascadesOnPermanentDelete() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.readerDao()
            dao.upsertState(ReadingStateEntity("d1", "4", 0.5f, 10L))
            dao.upsertState(ReadingStateEntity("d1", "7", 0.8f, 20L)) // upsert: still one row
            assertEquals("7", dao.getState("d1")?.locator)

            assertTrue(dao.toggleBookmark(BookmarkEntity("b1", "d1", "7", null, 1L)))
            assertEquals(listOf("7"), dao.observeBookmarkLocators("d1").first())
            assertFalse(dao.toggleBookmark(BookmarkEntity("b2", "d1", "7", null, 2L))) // second toggle removes
            assertEquals(emptyList<String>(), dao.observeBookmarkLocators("d1").first())

            dao.toggleBookmark(BookmarkEntity("b3", "d1", "2", null, 3L))
            db.documentDao().getById("d1") // document survives migration
            db.trashDao().softDelete("d1", 5L)
            assertNotNull(dao.getState("d1")) // Trash keeps progress (Restore-safe)
            db.trashDao().deleteTrashedRow("d1")
            assertEquals(null, dao.getState("d1")) // permanent delete cascades
            assertEquals(emptyList<String>(), dao.observeBookmarkLocators("d1").first())
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationAddsWorkingAnnotationsTableAndCascadesOnPermanentDelete() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.annotationDao()
            val row = AnnotationEntity("a1", "d1", "3", "highlight", null, 0.1f, 0.2f, 0.5f, 0.3f, null, 1L, 1L)
            dao.insert(row)
            dao.insert(row.copy(type = "underline")) // retried insert with same id: ignored (idempotent)
            assertEquals(listOf("highlight"), dao.observeForDocument("d1").first().map { it.type })

            assertEquals(1, dao.update("a1", "note", "green", "hello", 9L))
            val updated = dao.observeForDocument("d1").first().single()
            assertEquals("note", updated.type)
            assertEquals("hello", updated.noteText)
            assertEquals("green", updated.color)
            assertEquals(0.5f, updated.rectRight, 0f) // rect untouched by update

            db.trashDao().softDelete("d1", 5L)
            assertEquals(1, dao.observeForDocument("d1").first().size) // Trash keeps annotations (Restore-safe)
            db.trashDao().deleteTrashedRow("d1")
            assertEquals(emptyList<AnnotationEntity>(), dao.observeForDocument("d1").first()) // cascade
        } finally {
            db.close()
        }
    }

    @Test
    fun v5RectsColumnStoresMultiPartAndOldRowsReadNull() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.annotationDao()
            dao.insert(AnnotationEntity("old", "d1", "1", "underline", null, 0.1f, 0.2f, 0.5f, 0.3f, null, 1L, 1L))
            val json = "[[0.1,0.2,0.5,0.3]]"
            val multi = AnnotationEntity("new", "d1", "1", "underline", null, 0.1f, 0.2f, 0.9f, 0.7f, null, 2L, 2L)
                .copy(rects = json)
            dao.insert(multi)
            val rows = dao.observeForDocument("d1").first().associateBy { it.annotationId }
            assertEquals(null, rows.getValue("old").rects)
            assertEquals("[[0.1,0.2,0.5,0.3]]", rows.getValue("new").rects)
        } finally {
            db.close()
        }
    }

    @Test
    fun v6ScanPagesStoreUpsertDeleteAndCascade() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.scanPageDao()
            dao.upsert(ScanPageEntity("d1", 0, "Cover", null, 1L))
            dao.upsert(ScanPageEntity("d1", 0, "Cover", "note", 2L)) // same key = replace, not duplicate
            assertEquals("note", dao.observe("d1", 0).first()?.note)
            dao.delete("d1", 0)
            assertEquals(null, dao.observe("d1", 0).first())
            dao.upsert(ScanPageEntity("d1", 1, "Back", null, 3L))
            db.trashDao().softDelete("d1", 5L)
            db.trashDao().deleteTrashedRow("d1")
            assertEquals(null, dao.observe("d1", 1).first()) // cascade with the document
        } finally {
            db.close()
        }
    }

    @Test
    fun v7SyncItemsTableWorksAndDeleteItemOutlivesItsDocument() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.syncItemDao()
            dao.enqueue("document", "d1", "PUT", 1L)
            dao.enqueue("document", "d1", "DELETE", 2L) // coalesced into the same row
            db.trashDao().softDelete("d1", 5L)
            db.trashDao().deleteTrashedRow("d1") // no FK: the queue row must survive the document
            assertEquals(1, dao.getDue("FAILED", Long.MAX_VALUE, 100).size)
            assertEquals("DELETE", dao.getDue("FAILED", 10L, 10).single().operation)
        } finally {
            db.close()
        }
    }

    @Test
    fun v8SyncBaseTableWorks() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.syncItemDao()
            assertEquals(null, dao.getBase("d1")) // existing documents start without a base
            dao.setBase(SyncBaseEntity("d1", 2L))
            dao.setBase(SyncBaseEntity("d1", 3L)) // replaces, never duplicates
            assertEquals(3L, dao.getBase("d1"))
            assertEquals(1, dao.deleteBase("d1"))
            assertEquals(1, db.aggregateDao().getAllActive().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v9ScanPageEditTableWorks() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.scanPageEditDao()
            assertEquals(0, dao.getForDocument("d1").size) // existing documents start without edit rows
            dao.upsertAll(listOf(ScanPageEditEntity("d1", 0, "p0.img", 90, null, "BW", 1L)))
            dao.upsertAll(listOf(ScanPageEditEntity("d1", 0, "p0.img", 180, null, "GRAY", 2L))) // replaces
            assertEquals(180, dao.getForDocument("d1").single().rotation)
            assertEquals(1, db.aggregateDao().getAllActive().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v11StrokeColumnStoresThicknessAndOldRowsReadNull() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.annotationDao()
            dao.insert(AnnotationEntity("old", "d1", "1", "underline", null, 0.1f, 0.2f, 0.5f, 0.3f, null, 1L, 1L))
            dao.insert(
                AnnotationEntity("new", "d1", "1", "underline", null, 0.1f, 0.2f, 0.5f, 0.3f, null, 2L, 2L)
                    .copy(stroke = "thick"),
            )
            assertEquals(1, dao.setStroke("old", "thin"))
            val rows = dao.observeForDocument("d1").first().associateBy { it.annotationId }
            assertEquals("thin", rows.getValue("old").stroke)
            assertEquals("thick", rows.getValue("new").stroke)
            dao.setStroke("old", null)
            assertEquals(null, dao.observeForDocument("d1").first().first { it.annotationId == "old" }.stroke)
        } finally {
            db.close()
        }
    }

    @Test
    fun v10ScanOcrTableWorks() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.scanOcrDao()
            assertEquals(0, dao.getForDocument("d1").size) // existing documents start without OCR rows
            dao.upsert(ScanOcrEntity("d1", 0, "old", "ben+eng", 1, 1L))
            dao.upsert(ScanOcrEntity("d1", 0, "100% sure_ম", "ben+eng", 1, 2L)) // replaces, never duplicates
            dao.upsert(ScanOcrEntity("d1", 1, "", "ben+eng", 1, 2L)) // blank page is still "read"
            assertEquals(setOf(0, 1), dao.donePages("d1", 1).toSet())
            assertEquals(0, dao.donePages("d1", 2).size) // other engine version = read again
            assertEquals(listOf("d1"), dao.observeMatching(escapeLike("100%")).first())
            assertEquals(0, dao.observeMatching(escapeLike("1_0")).first().size) // `_` is literal, not a wildcard
            assertEquals(listOf("d1"), dao.observeMatching(escapeLike("ম")).first())
            assertEquals(2, dao.deleteForDocument("d1"))
            assertEquals(1, db.aggregateDao().getAllActive().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun v12FolderTypeBackfillTypesOnlyUniformFoldersAndFullChainOpens() = runBlocking {
        val raw = SQLiteDatabase.create(null)
        raw.execSQL("CREATE TABLE folders (folderId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT)")
        raw.execSQL("CREATE TABLE documents (documentId TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, folderId TEXT)")
        listOf("fPdf", "fMixed", "fEmpty", "fEpub").forEach {
            raw.execSQL("INSERT INTO folders (folderId, name) VALUES ('$it', '$it')")
        }
        listOf(
            Triple("d1", "pdf", "fPdf"),
            Triple("d2", "pdf", "fPdf"),
            Triple("d3", "pdf", "fMixed"),
            Triple("d4", "epub", "fMixed"),
            Triple("d5", "epub", "fEpub"),
        ).forEach { (id, type, folder) -> raw.execSQL("INSERT INTO documents VALUES ('$id', '$type', '$folder')") }
        raw.execSQL(PaperlyMigrations.BACKFILL_FOLDER_TYPE)
        val types = raw.rawQuery("SELECT folderId, type FROM folders", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        raw.close()
        assertEquals(mapOf("fPdf" to "pdf", "fMixed" to null, "fEmpty" to null, "fEpub" to "epub"), types)

        createV1Database()
        val db = Room.databaseBuilder(context, PaperlyDatabase::class.java, TEST_DB)
            .addMigrations(*PaperlyMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            db.folderDao().insert(FolderEntity("f1", "Work", null, 5L, "epub"))
            db.folderDao().insert(FolderEntity("f2", "Old", null, 6L))
            val byId = db.folderDao().getAll().associateBy { it.folderId }
            assertEquals("epub", byId.getValue("f1").type)
            assertEquals(null, byId.getValue("f2").type)
            assertNotNull(db.documentDao().getById("d1")) // documents survive the whole chain
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val V1_DOCUMENTS =
            "CREATE TABLE IF NOT EXISTS `documents` (`documentId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `checksum` TEXT NOT NULL, `localUri` TEXT, " +
                "`cloudRef` TEXT, `storageState` TEXT NOT NULL, `isPasswordProtected` INTEGER NOT NULL, " +
                "`folderId` TEXT, `tags` TEXT, `isFavorite` INTEGER NOT NULL, `isSensitive` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER, " +
                "`deletedAt` INTEGER, `convertedFrom` TEXT, `schemaVersion` INTEGER NOT NULL, " +
                "PRIMARY KEY(`documentId`))"
        val INDEXES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_documents_checksum` ON `documents` (`checksum`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_folderId` ON `documents` (`folderId`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_deletedAt` ON `documents` (`deletedAt`)",
        )
    }
}
