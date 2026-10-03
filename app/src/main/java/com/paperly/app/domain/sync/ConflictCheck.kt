package com.paperly.app.domain.sync

/**
 * P8-E1: decides whether this device may overwrite the cloud copy of a document's metadata. Pure and SDK-free.
 *
 * - [baseUpdatedAt] = the cloud version (its `updatedAt`) this device last saw, or null if never synced.
 * - [remoteUpdatedAt] = the cloud version right now, or null if the cloud has no such document.
 * - [localUpdatedAt] = the version this device wants to write.
 *
 * Normal case: compare against the version we last saw, never wall-clocks, so a wrong phone clock cannot hide a
 * conflict. [remoteUpdatedAt] == [localUpdatedAt] means our write already landed (e.g. the app died before it could
 * record that): writing again is harmless.
 *
 * Fallback (no base, e.g. synced before conflict tracking existed): the cloud copy is only replaced if it is not
 * newer than ours. Any doubt means CONFLICT; nothing is ever overwritten silently.
 */
object ConflictCheck {
    fun canWrite(baseUpdatedAt: Long?, remoteUpdatedAt: Long?, localUpdatedAt: Long): Boolean = when {
        baseUpdatedAt != null -> remoteUpdatedAt == baseUpdatedAt || remoteUpdatedAt == localUpdatedAt
        else -> remoteUpdatedAt == null || remoteUpdatedAt <= localUpdatedAt
    }
}
