package com.paperly.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Sequential, numbered migrations (App Dev Rules §৩). Additive only.
 * Hand-written (not AutoMigration): `app/schemas` is not committed, so Room has no v1 JSON to diff.
 * SQL must match exactly what Room generates for the entities; PaperlyMigrationTest guards that.
 */
object PaperlyMigrations {
    /** v1 → v2: add `folders` (documents already carries `folderId`/`tags`, so it is untouched). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`folderId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`parentFolderId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`folderId`))",
            )
        }
    }

    /** v2 → v3: add `reading_state` and `bookmarks` (both FK → documents, CASCADE). Existing tables untouched. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reading_state` (`documentId` TEXT NOT NULL, `locator` TEXT NOT NULL, " +
                    "`progressPercent` REAL NOT NULL, `lastOpenedAt` INTEGER NOT NULL, PRIMARY KEY(`documentId`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (`bookmarkId` TEXT NOT NULL, `documentId` TEXT NOT NULL, " +
                    "`locator` TEXT NOT NULL, `title` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`bookmarkId`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_documentId_locator` " +
                    "ON `bookmarks` (`documentId`, `locator`)",
            )
        }
    }

    /** v3 → v4: add `annotations` (FK → documents, CASCADE). Existing tables untouched. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `annotations` (`annotationId` TEXT NOT NULL, `documentId` TEXT NOT NULL, " +
                    "`locator` TEXT NOT NULL, `type` TEXT NOT NULL, `color` TEXT, `rectLeft` REAL NOT NULL, " +
                    "`rectTop` REAL NOT NULL, `rectRight` REAL NOT NULL, `rectBottom` REAL NOT NULL, " +
                    "`noteText` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`annotationId`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_annotations_documentId` ON `annotations` (`documentId`)",
            )
        }
    }

    /** v4 → v5: add nullable `annotations.rects` (multi-part annotations). Old rows keep `rects = NULL`. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `annotations` ADD COLUMN `rects` TEXT")
        }
    }

    /** v5 → v6: add `scan_pages` (per-page title/note of scanned documents; FK → documents, CASCADE). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_pages` (`documentId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                    "`title` TEXT, `note` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`documentId`, `pageIndex`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** v6 → v7: add `sync_items` (the sync queue). New table only; no FK so a DELETE item outlives its document. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_items` (`syncId` TEXT NOT NULL, `entityType` TEXT NOT NULL, " +
                    "`entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                    "`attempts` INTEGER NOT NULL, `nextRetryAt` INTEGER, `lastError` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`syncId`))",
            )
        }
    }

    /** v7 → v8: add `sync_base` (last seen cloud version per document, for conflict detection). New table only. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_base` (`documentId` TEXT NOT NULL, " +
                    "`remoteUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`documentId`))",
            )
        }
    }

    /** Every migration in order: the single list used by the app and by tests. */
    val ALL = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
}
