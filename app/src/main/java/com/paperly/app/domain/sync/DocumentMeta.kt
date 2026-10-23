package com.paperly.app.domain.sync

/**
 * OK = stored; RETRY = transient (offline, timeout, server busy);
 * DENIED = rules rejected it (do not retry blindly);
 * DEFERRED = cannot proceed until the user acts (e.g. allows Drive access): parked, no failed attempt counted;
 * CONFLICT = the cloud copy changed since this device last saw it: nothing was written.
 */
enum class RemoteResult { OK, RETRY, DENIED, DEFERRED, CONFLICT }

/**
 * Firestore access for the old per-document metadata. Only what "Delete all my data" still needs is kept: listing
 * and deleting. Every call is scoped to one signed-in [uid].
 */
interface RemoteMetadataStore {
    suspend fun deleteDocument(uid: String, documentId: String): RemoteResult

    /** Ids of every document in the cloud (server truth, no limit: a partial set would be wrong). null = failed. */
    suspend fun listDocumentIds(uid: String): Set<String>?
}
