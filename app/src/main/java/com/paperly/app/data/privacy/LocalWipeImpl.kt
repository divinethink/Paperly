package com.paperly.app.data.privacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.work.WorkManager
import com.paperly.app.core.database.PaperlyDatabase
import com.paperly.app.core.file.DOCUMENTS_DIR
import com.paperly.app.domain.privacy.LocalWipe
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P9-E2 phone part. Order: marker first (a crash from here on finishes the wipe at next start), then background
 * work, database, files, settings; the marker goes last. Each step is repeatable, so resuming is just running again.
 * Not touched: backup ZIPs the user saved elsewhere, and what other devices hold (see the Privacy text).
 */
@Singleton
class LocalWipeImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PaperlyDatabase,
    private val settings: DataStore<Preferences>,
) : LocalWipe {
    private val marker: File get() = File(context.noBackupFilesDir, MARKER)

    override suspend fun wipe() {
        withContext(Dispatchers.IO) {
            check(marker.exists() || marker.createNewFile()) { "cannot write wipe marker" }
            runCatching { WorkManager.getInstance(context).cancelAllWork() } // absent only in plain unit tests
            db.clearAllTables()
            deleteContents(File(context.filesDir, DOCUMENTS_DIR))
            deleteContents(File(context.filesDir, "db-backups"))
            deleteContents(context.cacheDir)
            settings.edit { it.clear() }
            check(marker.delete() || !marker.exists()) { "cannot remove wipe marker" }
        }
    }

    override suspend fun resumeIfInterrupted() {
        if (marker.exists()) wipe()
    }

    private fun deleteContents(dir: File) {
        dir.listFiles().orEmpty().forEach { check(it.deleteRecursively()) { "cannot delete ${it.name}" } }
    }

    private companion object {
        const val MARKER = "delete_all.marker"
    }
}
