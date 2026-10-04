package com.paperly.app.domain.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Optional account. Paperly is fully usable while [AuthState.SignedOut] or [AuthState.Unavailable]. */
sealed interface AuthState {
    /** This build has no Firebase configuration (no google-services.json), so sync cannot be offered. */
    data object Unavailable : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val uid: String, val email: String?) : AuthState
}

enum class SignInResult { SIGNED_IN, CANCELLED, NO_ACCOUNT, FAILED }

/** NEEDS_RECENT_LOGIN = Firebase wants a fresh sign-in before it deletes the account; nothing was changed. */
enum class DeleteAccountResult { DELETED, NEEDS_RECENT_LOGIN, FAILED }

interface AuthRepository {
    val state: Flow<AuthState>

    /** Technical reason of the last failed [signIn] (error type + APK SHA-1) for Settings; no user data. */
    val lastErrorDetail: String? get() = null

    /** [activity] is only used to show the account chooser; it is not retained. */
    suspend fun signIn(activity: Context): SignInResult

    suspend fun signOut(activity: Context)

    /** Deletes the signed-in Firebase account itself (its cloud data is wiped by the caller first). */
    suspend fun deleteAccount(): DeleteAccountResult = DeleteAccountResult.FAILED
}
