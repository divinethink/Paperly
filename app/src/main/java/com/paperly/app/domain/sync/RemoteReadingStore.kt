package com.paperly.app.domain.sync

/** Firestore access for reading positions (users/{uid}/readingState/{documentId}). Writes are idempotent. */
interface RemoteReadingStore {
    /** Stores [meta] unless the cloud already holds a newer position (then nothing is written; still OK). */
    suspend fun putReading(uid: String, meta: ReadingMeta): RemoteResult

    suspend fun deleteReading(uid: String, documentId: String): RemoteResult

    /** Complete set of positions with updatedAt > [updatedAfter], read from the server. null = failed. */
    suspend fun fetchReadingSince(uid: String, updatedAfter: Long): List<ReadingMeta>?
}
