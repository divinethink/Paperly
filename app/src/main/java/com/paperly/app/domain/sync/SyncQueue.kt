package com.paperly.app.domain.sync

/**
 * Records "this changed, sync it" for the cloud. Every call is best-effort and never throws: a sync-queue problem
 * must not break a library write (the local library is the source of truth, sync is a mirror).
 */
interface SyncQueue {
    suspend fun documentChanged(id: String)

    suspend fun documentsChanged(ids: List<String>)

    /** The document row is gone for good (permanent delete). */
    suspend fun documentDeleted(id: String)

    /** After a bulk change (e.g. a backup restore): queue every document. */
    suspend fun allDocumentsChanged()

    /** Ask for a sync run now (e.g. right after sign-in). */
    suspend fun kick()
}

/** Delay before the next try of an item that failed transiently: 30 s, 60 s, 2 min ... capped at 6 h. */
object SyncBackoff {
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 6L * 60 * 60 * 1000
    private const val MAX_SHIFT = 10

    fun delayMs(attempts: Int): Long {
        val shift = (attempts - 1).coerceIn(0, MAX_SHIFT)
        return minOf(BASE_MS shl shift, MAX_MS)
    }
}
