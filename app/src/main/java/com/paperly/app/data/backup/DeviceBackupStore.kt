package com.paperly.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.paperly.app.domain.backup.BackupScope
import com.paperly.app.domain.backup.BackupTarget
import com.paperly.app.domain.backup.LastBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val DEVICE_FOLDER = stringPreferencesKey("backup_device_folder")
private const val BACKUP_MIME = "application/octet-stream"
private const val NEW_SUFFIX = ".new"
private const val OLD_SUFFIX = ".old"
private const val LAST_SEPARATOR = '|'

/** "DRIVE|1700000000000" — the preference value of one scope's last backup. */
internal fun encodeLastBackup(last: LastBackup): String = "${last.target.name}$LAST_SEPARATOR${last.at}"

internal fun decodeLastBackup(raw: String?): LastBackup? {
    val name = raw?.substringBefore(LAST_SEPARATOR)
    val target = BackupTarget.entries.firstOrNull { it.name == name }
    val at = raw?.substringAfter(LAST_SEPARATOR, "")?.toLongOrNull()
    return if (target != null && at != null) LastBackup(target, at) else null
}

/** "primary:Documents/Backups" (the id inside a folder URI) -> "Documents/Backups". */
internal fun folderLabelOf(treeDocumentId: String): String =
    treeDocumentId.substringAfter(':').ifEmpty { treeDocumentId }

private fun lastKey(scope: BackupScope) = stringPreferencesKey("backup_last_" + scope.name.lowercase())

/**
 * A folder on this device (chosen once through the system folder picker) that holds one ZIP per scope under a
 * fixed name. The new file is written and verified beside the old one; only then is the old one swapped out, so a
 * failure at any step leaves the previous backup in place. Also remembers the last successful backup per scope.
 */
@Singleton
class DeviceBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStore<Preferences>,
) {
    /** Label of the chosen folder, null = none chosen. */
    val folderLabel: Flow<String?> = settings.data.map { prefs ->
        prefs[DEVICE_FOLDER]?.let { label(Uri.parse(it)) }
    }

    val lastBackups: Flow<Map<BackupScope, LastBackup>> = settings.data.map { prefs ->
        BackupScope.entries.mapNotNull { scope -> decodeLastBackup(prefs[lastKey(scope)])?.let { scope to it } }.toMap()
    }

    suspend fun recordBackup(scope: BackupScope, last: LastBackup) {
        settings.edit { it[lastKey(scope)] = encodeLastBackup(last) }
    }

    /** Remembers the folder across restarts (the permission is kept by the system) and frees the previous one. */
    suspend fun setFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val previous = settings.data.first()[DEVICE_FOLDER]?.let(Uri::parse)
        withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.takePersistableUriPermission(tree, flags) }
            if (previous != null && previous != tree) {
                runCatching { context.contentResolver.releasePersistableUriPermission(previous, flags) }
            }
        }
        settings.edit { it[DEVICE_FOLDER] = tree.toString() }
    }

    /** true = [file] is in the folder under [name], verified, and any older copy is gone. */
    suspend fun save(file: File, name: String): Boolean {
        val tree = settings.data.first()[DEVICE_FOLDER]?.let(Uri::parse) ?: return false
        return withContext(Dispatchers.IO) {
            try {
                saveInto(tree, file, name)
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false // the folder permission was taken away
            } catch (e: IllegalArgumentException) {
                false // the folder no longer exists
            }
        }
    }

    private fun label(tree: Uri): String? = try {
        folderLabelOf(DocumentsContract.getTreeDocumentId(tree))
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun saveInto(tree: Uri, file: File, name: String): Boolean {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        findChild(tree, name + NEW_SUFFIX)?.let { DocumentsContract.deleteDocument(resolver, it) }
        val fresh = DocumentsContract.createDocument(resolver, parent, BACKUP_MIME, name + NEW_SUFFIX) ?: return false
        val done = copyAndVerify(fresh, file) && swapIn(tree, fresh, name)
        if (!done) findChild(tree, name + NEW_SUFFIX)?.let { DocumentsContract.deleteDocument(resolver, it) }
        return done
    }

    private fun copyAndVerify(target: Uri, file: File): Boolean {
        val resolver = context.contentResolver
        val written = resolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return written != null && resolver.openInputStream(target)?.use { BackupArchive.verify(it) } == true
    }

    /** Old copy aside -> new copy gets the real name -> old copy deleted. Any failure puts the old copy back. */
    private fun swapIn(tree: Uri, fresh: Uri, name: String): Boolean {
        val resolver = context.contentResolver
        findChild(tree, name + OLD_SUFFIX)?.let { DocumentsContract.deleteDocument(resolver, it) }
        val old = findChild(tree, name)
        val aside = old?.let { DocumentsContract.renameDocument(resolver, it, name + OLD_SUFFIX) }
        if (old != null && aside == null) return false // the old copy could not be moved: nothing changed
        if (DocumentsContract.renameDocument(resolver, fresh, name) == null) {
            aside?.let { DocumentsContract.renameDocument(resolver, it, name) }
            return false
        }
        aside?.let { DocumentsContract.deleteDocument(resolver, it) }
        return true
    }

    private fun findChild(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        context.contentResolver.query(children, columns, null, null, null)?.use { rows ->
            while (rows.moveToNext()) {
                if (rows.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(0))
                }
            }
        }
        return null
    }
}
