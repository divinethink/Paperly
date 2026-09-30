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
}
