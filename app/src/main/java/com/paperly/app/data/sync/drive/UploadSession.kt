package com.paperly.app.data.sync.drive

/** An interrupted upload that can continue. Tied to the exact content (sha256 + size) it was started for. */
data class UploadSession(val url: String, val sha256: String, val size: Long)

/** Survives process death, so a big upload continues instead of starting over. */
interface UploadSessionStore {
    suspend fun get(documentId: String): UploadSession?

    suspend fun save(documentId: String, session: UploadSession)

    suspend fun clear(documentId: String)

    /** Another Google account signed in: sessions belong to the old account's Drive and must never be resumed. */
    suspend fun clearAll()
}
