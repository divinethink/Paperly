package com.paperly.app.data.sync.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.paperly.app.data.auth.await
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.auth.DriveToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

/**
 * Google's AuthorizationClient for the drive.appdata scope only (the app's own hidden folder; no access to the
 * user's other Drive files). Tokens are short-lived and kept in memory by Play services, never by us.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
) : DriveAuth {

    override suspend fun token(): DriveToken {
        val signedIn = auth.state.first() as? AuthState.SignedIn ?: return DriveToken.Unavailable
        return attempt {
            val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            signedIn.email?.let { builder.setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) }
            Identity.getAuthorizationClient(context).authorize(builder.build()).await()
        }
    }

    override suspend fun invalidate(token: String) {
        try {
            val request = ClearTokenRequest.builder().setToken(token).build()
            Identity.getAuthorizationClient(context).clearToken(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
            // best effort: the next authorize() call returns a fresh token anyway
        }
    }

    override suspend fun tokenFromConsent(result: Intent?): DriveToken =
        attempt { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result) }

    private suspend fun attempt(block: suspend () -> AuthorizationResult): DriveToken = try {
        block().toToken()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DriveToken.Unavailable // no Play services, user cancelled, API error: sync just waits
    }

    private fun AuthorizationResult.toToken(): DriveToken {
        val pending = pendingIntent
        val value = accessToken
        return when {
            hasResolution() && pending != null -> DriveToken.NeedsConsent(pending.intentSender)
            value != null -> DriveToken.Ready(value)
            else -> DriveToken.Unavailable
        }
    }
}
