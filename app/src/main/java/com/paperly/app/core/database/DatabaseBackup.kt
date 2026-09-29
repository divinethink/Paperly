package com.paperly.app.core.database

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Zero-data-loss guard (App Dev Rules §৩): before Room migrates an older DB file, keep a verified copy.
 * Fails loudly (IllegalStateException) if the copy cannot be verified, so no unbacked migration runs.
 */
object DatabaseBackup {
    fun backupBeforeMigration(dbFile: File, backupDir: File, targetVersion: Int) {
        if (!dbFile.exists()) return
        val version = checkpointAndReadVersion(dbFile)
        if (version <= 0 || version >= targetVersion) return // 0 = brand-new/empty DB, nothing to protect

        check(backupDir.isDirectory || backupDir.mkdirs()) { "Cannot create DB backup directory" }
        val target = File(backupDir, "${dbFile.name}.v$version.bak")
        val temp = File(backupDir, "${target.name}.tmp")
        dbFile.copyTo(temp, overwrite = true)
        check(temp.length() == dbFile.length() && readVersion(temp) == version) { "DB backup verification failed" }
        check(!target.exists() || target.delete()) { "Cannot replace previous DB backup" }
        check(temp.renameTo(target)) { "Cannot finalize DB backup" }
    }

    /** Folds any WAL content into the main file so a plain file copy is complete. */
    private fun checkpointAndReadVersion(dbFile: File): Int =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.version
        }

    private fun readVersion(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
}
