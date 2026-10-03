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

interface AuthRepository {
    val state: Flow<AuthState>

    /** [activity] is only used to show the account chooser; it is not retained. */
    suspend fun signIn(activity: Context): SignInResult

    /** Short technical reason of the last non-successful [signIn], for on-screen diagnosis; null otherwise. */
    val lastSignInError: String? get() = null

    suspend fun signOut(activity: Context)
}
