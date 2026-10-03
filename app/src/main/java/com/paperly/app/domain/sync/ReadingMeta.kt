package com.paperly.app.domain.sync

/**
 * The part of a reading position that is synced to Firestore (P8-E4): where to resume, never book content.
 * [updatedAt] = local `lastOpenedAt` (UTC epoch millis); the newer one wins.
 */
data class ReadingMeta(
    val documentId: String,
    val locator: String,
    val progressPercent: Float,
    val updatedAt: Long,
    val schemaVersion: Int = 1,
)

enum class ReadingPullAction {
    /** Take the cloud position. */
    APPLY,

    /** Keep the local position (newer or equal, or this device has an unsent change). */
    SKIP,
}

/** Pure rules for reading-position sync. Low-risk data, so no conflict dialog: the newer position simply wins. */
object ReadingPullDecision {
    /** Push side: never move the cloud copy backwards. Equal = rewrite (idempotent). */
    fun shouldPush(remoteUpdatedAt: Long?, localUpdatedAt: Long): Boolean =
        remoteUpdatedAt == null || localUpdatedAt >= remoteUpdatedAt

    /** Pull side: an unsent local position is never overwritten (it is pushed first, and push is newer-wins too). */
    fun decide(localUpdatedAt: Long?, hasPendingItem: Boolean, remote: ReadingMeta): ReadingPullAction = when {
        hasPendingItem -> ReadingPullAction.SKIP
        localUpdatedAt == null -> ReadingPullAction.APPLY
        remote.updatedAt > localUpdatedAt -> ReadingPullAction.APPLY
        else -> ReadingPullAction.SKIP
    }
}

/** Quota guard (Roadmap Dev Rule #6): at most one queue entry per document per interval; a pause/close flushes. */
object ReadingDebounce {
    const val INTERVAL_MS = 60_000L

    /** A clock that went backwards counts as "long ago", so a changed clock cannot silence sync. */
    fun shouldEnqueue(lastEnqueuedAt: Long?, now: Long): Boolean =
        lastEnqueuedAt == null || now < lastEnqueuedAt || now - lastEnqueuedAt >= INTERVAL_MS
}
