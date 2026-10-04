package com.paperly.app.domain.sync

/** Why a clean-up did not start. Nothing is deleted whenever the picture is incomplete or sync is busy. */
enum class CleanupBlock { NOT_SIGNED_IN, PAUSED, SYNC_BUSY, NEEDS_ACCESS, UNAVAILABLE }

sealed interface CleanupPreview {
    data class Blocked(val reason: CleanupBlock) : CleanupPreview

    data class Found(val count: Int, val bytes: Long) : CleanupPreview
}

sealed interface CleanupResult {
    data class Blocked(val reason: CleanupBlock) : CleanupResult

    /** [remaining] = files that were left (could not be removed, or turned out to belong to a document). */
    data class Done(val deleted: Int, val remaining: Int) : CleanupResult
}

/** Manual removal of unused cloud files (P8-F2). Never runs by itself. */
interface CloudCleanup {
    suspend fun preview(now: Long = System.currentTimeMillis()): CleanupPreview

    /** Looks everything up again (never trusts an earlier preview) and re-checks each document right before delete. */
    suspend fun clean(now: Long = System.currentTimeMillis()): CleanupResult
}
