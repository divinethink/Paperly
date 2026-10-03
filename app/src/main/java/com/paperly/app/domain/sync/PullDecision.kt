package com.paperly.app.domain.sync

/** What to do with one document that came down from the cloud. */
enum class PullAction {
    /** New here: add it (metadata only; the file is downloaded afterwards). */
    INSERT,

    /** The cloud's copy is newer and this device has no unsynced change: take the cloud's values. */
    APPLY,

    /** Same version as here: only remember that it was seen. */
    MARK_SEEN,

    /** Leave it (already seen, older than ours, or this device has an unsynced change). */
    SKIP,
}

/**
 * P8-E2: pure rules for incoming cloud documents. An unsynced local change is never overwritten here: it is
 * pushed later, and if the cloud moved on meanwhile the push is stopped as a conflict (see [ConflictCheck]).
 */
object PullDecision {
    fun decide(
        localUpdatedAt: Long?,
        baseUpdatedAt: Long?,
        hasPendingItem: Boolean,
        remote: DocumentMeta,
    ): PullAction = when {
        // Not here. A pending item (e.g. a permanent delete not sent yet) or a cloud copy in Trash adds nothing.
        localUpdatedAt == null -> if (hasPendingItem || remote.deletedAt != null) PullAction.SKIP else PullAction.INSERT
        hasPendingItem -> PullAction.SKIP
        baseUpdatedAt == remote.updatedAt -> PullAction.SKIP
        remote.updatedAt == localUpdatedAt -> PullAction.MARK_SEEN
        remote.updatedAt > localUpdatedAt -> PullAction.APPLY
        else -> PullAction.SKIP
    }
}
