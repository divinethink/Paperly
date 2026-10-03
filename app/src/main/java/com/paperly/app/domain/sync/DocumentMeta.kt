package com.paperly.app.domain.sync

/**
 * The part of a Document that is synced to Firestore (metadata only; never file content, local paths or
 * sensitive/unused columns). Times are UTC epoch millis, same as the local table.
 */
data class DocumentMeta(
    val documentId: String,
    val title: String,
    val type: String,
    val sizeBytes: Long,
    val checksum: String,
    val folderId: String? = null,
    val tags: List<String>? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val schemaVersion: Int = 1,
)

/**
 * OK = stored; RETRY = transient (offline, timeout, server busy);
 * DENIED = rules rejected it (do not retry blindly);
 * DEFERRED = cannot proceed until the user acts (e.g. allows Drive access): parked, no failed attempt counted.
 */
enum class RemoteResult { OK, RETRY, DENIED, DEFERRED }

/** Firestore access for document metadata. Every call is scoped to one signed-in [uid]; writes are idempotent. */
interface RemoteMetadataStore {
    suspend fun putDocument(uid: String, meta: DocumentMeta): RemoteResult

    suspend fun deleteDocument(uid: String, documentId: String): RemoteResult

    /**
     * Complete set of documents with updatedAt > [updatedAfter]
     * (no limit: a truncated set would be wrong). null = failed.
     */
    suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>?
}
