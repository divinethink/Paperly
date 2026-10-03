package com.paperly.app.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Firebase Auth + Google sign-in via Credential Manager. All Firebase access is guarded by [configured]:
 * without google-services.json no FirebaseApp exists, and touching FirebaseAuth would throw.
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : AuthRepository {

    private val configured: Boolean get() = FirebaseApp.getApps(appContext).isNotEmpty()

    // google-services generates this string only when the plugin runs; look it up by name so the build
    // compiles (and stays green) without google-services.json.
    private val webClientId: String?
        get() = appContext.resources
            .getIdentifier(WEB_CLIENT_ID_RES, "string", appContext.packageName)
            .takeIf { it != 0 }
            ?.let(appContext::getString)

    @Volatile
    private var lastError: String? = null
    override val lastSignInError: String? get() = lastError

    override val state: Flow<AuthState> =
        if (!configured) {
            flowOf(AuthState.Unavailable)
        } else {
            callbackFlow {
                val auth = FirebaseAuth.getInstance()
                val listener = FirebaseAuth.AuthStateListener { trySend(auth.currentUser.toState()) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
        }

    override suspend fun signIn(activity: Context): SignInResult {
        lastError = null
        val clientId = webClientId
        if (!configured || clientId == null) {
            lastError = "no web client id in build"
            return SignInResult.FAILED
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            val idToken = (credential as? CustomCredential)
                ?.takeIf { it.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL }
                ?.let { GoogleIdTokenCredential.createFrom(it.data).idToken }
            if (idToken == null) {
                lastError = "no Google ID token"
                SignInResult.FAILED
            } else {
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                SignInResult.SIGNED_IN
            }
        } catch (e: GetCredentialCancellationException) {
            lastError = describe(e)
            SignInResult.CANCELLED
        } catch (e: NoCredentialException) {
            lastError = describe(e)
            SignInResult.NO_ACCOUNT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = describe(e)
            SignInResult.FAILED
        }
    }

    override suspend fun signOut(activity: Context) {
        if (!configured) return
        FirebaseAuth.getInstance().signOut()
        // Forget the chosen account so the next sign-in shows the chooser; failure here is harmless.
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { if (it is CancellationException) throw it }
    }

    private fun FirebaseUser?.toState(): AuthState =
        if (this == null) AuthState.SignedOut else AuthState.SignedIn(uid, email)

    private fun describe(e: Exception): String = "${e.javaClass.simpleName}: ${e.message.orEmpty().take(MAX_DETAIL)}"

    private companion object {
        const val MAX_DETAIL = 160
        const val WEB_CLIENT_ID_RES = "default_web_client_id"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
