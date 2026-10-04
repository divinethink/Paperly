package com.paperly.app.core.database

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Zero-data-loss guard (App Dev Rules §৩): before Room migrates an older DB file, keep a verified copy.
 * Fails loudly (IllegalStateException) if the copy cannot be verified, so no unbacked migration runs.
 */
object DatabaseBackup {
    /** How many pre-migration copies are kept (the highest versions). Each is only a few MB at most. */
    const val KEEP_BACKUPS = 3
    private val backupName = Regex("""^.+\.v(\d+)\.bak$""")

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
        prune(backupDir, KEEP_BACKUPS, alsoKeep = version) // only after the new copy is verified and in place
    }

    /** Our `<db>.v<N>.bak` files, newest schema version first. Anything else in the folder is not ours and ignored. */
    fun listBackups(backupDir: File): List<Pair<Int, File>> =
        backupDir.listFiles().orEmpty()
            .mapNotNull { f -> backupName.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to f } }
            .sortedByDescending { it.first }

    /**
     * Keeps the [keep] highest-version backups (plus [alsoKeep], the copy just made) and removes the rest and any
     * leftover `.tmp`. Version-based, not date-based: a clock change can never drop the newest schema's copy.
     * Best effort, never throws: failing to prune must not stop the app from starting. Returns files removed.
     */
    fun prune(backupDir: File, keep: Int = KEEP_BACKUPS, alsoKeep: Int? = null): Int {
        val all = listBackups(backupDir)
        val kept = all.take(keep).map { it.first }.toSet() + setOfNotNull(alsoKeep)
        val stale = all.filter { it.first !in kept }.map { it.second } +
            backupDir.listFiles().orEmpty().filter { it.name.endsWith(".bak.tmp") }
        return stale.count { it.delete() }
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
