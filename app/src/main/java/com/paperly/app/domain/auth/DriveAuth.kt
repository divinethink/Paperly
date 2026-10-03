package com.paperly.app.domain.auth

import android.content.Intent
import android.content.IntentSender

/** Access to the user's hidden Drive app folder (scope drive.appdata). Tokens live ~1 hour and are never stored. */
sealed interface DriveToken {
    class Ready(val value: String) : DriveToken {
        override fun toString(): String = "Ready" // never print the token
    }

    /** The user has not allowed Drive access yet: launch [request] from a screen. */
    class NeedsConsent(val request: IntentSender) : DriveToken

    /** Signed out, no Play services, or the call failed. */
    data object Unavailable : DriveToken
}

interface DriveAuth {
    suspend fun token(): DriveToken

    /** The server refused [token] (expired/revoked): drop it so the next [token] call gets a fresh one. */
    suspend fun invalidate(token: String)

    /** Call with the data returned by the consent screen launched from [DriveToken.NeedsConsent]. */
    suspend fun tokenFromConsent(result: Intent?): DriveToken
}
