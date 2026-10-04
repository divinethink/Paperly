package com.paperly.app.domain.privacy

import kotlinx.coroutines.flow.StateFlow

/** Why the cloud part stopped. The phone is untouched in every one of these cases. */
enum class CloudWipeBlock { NEEDS_ACCESS, OFFLINE_OR_FAILED, DENIED, NOT_EMPTY_AFTER }

sealed interface CloudWipeResult {
    /** Not signed in: nothing of this app is known to be in the cloud, so there is nothing to delete there. */
    data object NotSignedIn : CloudWipeResult

    /** Everything was deleted AND a fresh listing of all cloud stores came back empty. */
    data object Wiped : CloudWipeResult

    data class Stopped(val reason: CloudWipeBlock) : CloudWipeResult
}

/** Deletes every cloud copy (Drive files, Firestore documents and reading positions). Idempotent: safe to repeat. */
interface CloudWipe {
    suspend fun wipe(): CloudWipeResult
}

/** Deletes everything this app keeps on the phone. A marker makes an interrupted wipe finish at next start. */
interface LocalWipe {
    suspend fun wipe()

    /** At app start: finish a wipe that was cut short (no-op when none was). */
    suspend fun resumeIfInterrupted()
}

enum class DeleteStep { CLOUD, ACCOUNT, LOCAL }

/** NONE = there was no account to delete; KEPT = it could not be deleted (the user is signed out of it). */
enum class AccountStatus { NONE, DELETED, KEPT }

sealed interface DeleteOutcome {
    data class Done(val cloudWiped: Boolean, val account: AccountStatus) : DeleteOutcome

    /** Cloud part failed -> nothing was deleted on this phone. */
    data class CloudStopped(val reason: CloudWipeBlock) : DeleteOutcome

    /** Cloud + account are gone but some local data could not be removed (it is retried at next start). */
    data class LocalFailed(val cloudWiped: Boolean) : DeleteOutcome
}

data class DeleteRunState(val step: DeleteStep? = null, val outcome: DeleteOutcome? = null) {
    val busy: Boolean get() = step != null
}

/**
 * "Delete all my data". Runs in the app's own scope (leaving the screen never stops it halfway). Order is fixed:
 * cloud first, then the account, then this phone, so a failure leaves a state that can simply be retried.
 * Does nothing while a backup export/restore is running.
 */
interface DeleteAllDataRunner {
    val state: StateFlow<DeleteRunState>

    fun start()

    /** Clears a finished result. No effect while running. */
    fun dismissOutcome()
}
