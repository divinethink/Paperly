package com.paperly.app.data.sync.drive

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** DataStore-backed (app-private, excluded from backups). The session URL is a secret: never logged. */
@Singleton
class DataStoreUploadSessions @Inject constructor(
    private val store: DataStore<Preferences>,
) : UploadSessionStore {

    override suspend fun get(documentId: String): UploadSession? =
        store.data.first()[key(documentId)]?.let(::decode)

    override suspend fun save(documentId: String, session: UploadSession) {
        store.edit { it[key(documentId)] = encode(session) }
    }

    override suspend fun clear(documentId: String) {
        store.edit { it.remove(key(documentId)) }
    }

    override suspend fun clearAll() {
        store.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(KEY_PREFIX) }.forEach { prefs.remove(it) }
        }
    }

    private fun key(documentId: String) = stringPreferencesKey("$KEY_PREFIX$documentId")

    private fun encode(session: UploadSession) = "${session.size}\n${session.sha256}\n${session.url}"

    private fun decode(raw: String): UploadSession? {
        val parts = raw.split('\n', limit = PARTS)
        val size = parts.getOrNull(0)?.toLongOrNull()
        return if (parts.size == PARTS && size != null) UploadSession(parts[2], parts[1], size) else null
    }

    private companion object {
        const val KEY_PREFIX = "drive_upload_"
        const val PARTS = 3
    }
}
