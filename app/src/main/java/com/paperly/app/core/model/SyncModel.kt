package com.paperly.app.core.model

/** What a sync-queue row is about. Runtime-validated strings (extensible: folders etc. arrive with their sync step). */
object SyncEntityType {
    const val DOCUMENT = "document"

    /** The file behind a document (P8-D). Separate from DOCUMENT so metadata sync never waits for a big upload. */
    const val FILE = "file"
}

object SyncOperation {
    const val PUT = "PUT"
    const val DELETE = "DELETE"
}

/**
 * QUEUED/RETRYING = waiting, RUNNING = claimed by the worker, FAILED = rejected for good (until re-enqueued).
 * A conflict is a FAILED item with lastError = SYNC_ERROR_CONFLICT: it never retries by itself.
 */
object SyncItemState {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val RETRYING = "RETRYING"
    const val FAILED = "FAILED"
}

/** `lastError` of a FAILED item whose cloud copy changed elsewhere; the user decides (Settings -> Sync). */
const val SYNC_ERROR_CONFLICT = "conflict"

/** One row per entity: the id is deterministic, so enqueueing the same entity twice can never create a duplicate. */
fun syncIdOf(entityType: String, entityId: String): String = "$entityType:$entityId"
