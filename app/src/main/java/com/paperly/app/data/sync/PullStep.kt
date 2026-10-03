package com.paperly.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.database.SyncBaseEntity
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.core.model.DocumentType
import com.paperly.app.core.model.StorageState
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.syncIdOf
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.PullAction
import com.paperly.app.domain.sync.PullDecision
import com.paperly.app.domain.sync.RemoteMetadataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/**
 * P8-E2: brings the cloud's document metadata down. Each document is applied on its own and the rules live in
 * [PullDecision]; this class only reads the local state and performs the action. Applying twice changes nothing.
 */
@Singleton
class PullStep @Inject constructor(
    private val documents: DocumentDao,
    private val syncDao: SyncItemDao,
    private val cloud: CloudSyncDao,
    private val remote: RemoteMetadataStore,
    private val settings: DataStore<Preferences>,
) {
    /** A different account signed in: what the old one's cloud held is irrelevant. */
    suspend fun reset() {
        settings.edit { it.remove(LAST_PULL) }
    }

    /** false = the cloud could not be reached; run again later. */
    suspend fun pull(uid: String): Boolean {
        val last = settings.data.first()[LAST_PULL] ?: 0L
        // Looks back a day: a device with a slow clock may have written a version "older" than the last one seen.
        // Documents seen again are skipped by PullDecision, so this only costs a few extra reads.
        val metas = remote.fetchDocumentsSince(uid, (last - LOOKBACK_MS).coerceAtLeast(0L)) ?: return false
        metas.filter { SAFE_DOCUMENT_ID.matches(it.documentId) }.forEach { applyOne(it) }
        val newest = metas.maxOfOrNull { it.updatedAt }
        if (newest != null && newest > last) settings.edit { it[LAST_PULL] = newest }
        return true
    }

    private suspend fun applyOne(meta: DocumentMeta) {
        val local = documents.getById(meta.documentId)
        val pending = syncDao.countItem(syncIdOf(SyncEntityType.DOCUMENT, meta.documentId)) > 0
        val base = syncDao.getBase(meta.documentId)
        when (PullDecision.decide(local?.updatedAt, base, pending, meta)) {
            PullAction.INSERT -> if (insertable(meta)) {
                documents.insert(meta.toNewEntity())
                markSeen(meta)
            }
            PullAction.APPLY -> {
                with(meta) { cloud.applyRemote(documentId, title, tags, isFavorite, deletedAt, updatedAt) }
                markSeen(meta)
            }
            PullAction.MARK_SEEN -> markSeen(meta)
            PullAction.SKIP -> Unit
        }
    }

    private suspend fun markSeen(meta: DocumentMeta) = syncDao.setBase(SyncBaseEntity(meta.documentId, meta.updatedAt))

    /** The file is downloaded later by its checksum, so a cloud entry without a valid one cannot be used. */
    private fun insertable(meta: DocumentMeta) = DocumentType.isValid(meta.type) && SHA256_HEX.matches(meta.checksum)

    private fun DocumentMeta.toNewEntity() = DocumentEntity(
        documentId = documentId,
        title = title,
        type = type,
        sizeBytes = sizeBytes,
        checksum = checksum,
        storageState = StorageState.CLOUD_ONLY,
        folderId = null, // folders are not synced yet: a folder id that does not exist here would hide the document
        tags = tags,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        schemaVersion = schemaVersion,
    )

    companion object {
        private val LAST_PULL = longPreferencesKey("sync_last_pull")
        private const val LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
