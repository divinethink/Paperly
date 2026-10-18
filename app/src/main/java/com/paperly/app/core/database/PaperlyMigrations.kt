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

    /** v8 → v9: add `scan_page_edit` (S2 page editor state; FK → documents, CASCADE). New table only. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_page_edit` (`documentId` TEXT NOT NULL, " +
                    "`pageIndex` INTEGER NOT NULL, `srcFile` TEXT NOT NULL, `rotation` INTEGER NOT NULL, " +
                    "`cornersJson` TEXT, `filter` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`documentId`, `pageIndex`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** v9 → v10: add `scan_ocr` (S3 recognised text per scan page; FK → documents, CASCADE). New table only. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scan_ocr` (`documentId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                    "`text` TEXT NOT NULL, `lang` TEXT NOT NULL, `engineVersion` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`documentId`, `pageIndex`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** v10 → v11: add nullable `annotations.stroke` (line thickness). Old rows keep NULL = medium. Additive only. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `annotations` ADD COLUMN `stroke` TEXT")
        }
    }

    /**
     * Backfill for v12: a folder gets a type only when every document inside (Trash included) has the same one.
     * Empty or mixed folders stay NULL = visible in every tab, so nothing disappears.
     */
    const val BACKFILL_FOLDER_TYPE =
        "UPDATE `folders` SET `type` = " +
            "(SELECT MIN(d.`type`) FROM `documents` d WHERE d.`folderId` = `folders`.`folderId`) " +
            "WHERE (SELECT COUNT(DISTINCT d.`type`) FROM `documents` d " +
            "WHERE d.`folderId` = `folders`.`folderId`) = 1"

    /** v11 → v12: add nullable `folders.type` (Library tab the folder belongs to) and backfill it. Additive only. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `folders` ADD COLUMN `type` TEXT")
            db.execSQL(BACKFILL_FOLDER_TYPE)
        }
    }

    /** v12 → v13: add `scan_page_edit.brightness` / `contrast` (manual page adjustment). Additive, default 0. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `scan_page_edit` ADD COLUMN `brightness` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `scan_page_edit` ADD COLUMN `contrast` INTEGER NOT NULL DEFAULT 0")
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
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
    )
}
