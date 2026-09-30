package com.paperly.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
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

            assertEquals(1, dao.update("a1", "note", "hello", 9L))
            val updated = dao.observeForDocument("d1").first().single()
            assertEquals("note", updated.type)
            assertEquals("hello", updated.noteText)
            assertEquals(0.5f, updated.rectRight, 0f) // rect untouched by update

            db.trashDao().softDelete("d1", 5L)
            assertEquals(1, dao.observeForDocument("d1").first().size) // Trash keeps annotations (Restore-safe)
            db.trashDao().deleteTrashedRow("d1")
            assertEquals(emptyList<AnnotationEntity>(), dao.observeForDocument("d1").first()) // cascade
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
