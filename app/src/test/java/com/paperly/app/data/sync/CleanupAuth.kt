package com.paperly.app.data.sync

import android.content.Context
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class CleanupAuth(private val current: AuthState = AuthState.SignedIn("u1", null)) : AuthRepository {
    override val state: Flow<AuthState> = flowOf(current)

    override suspend fun signIn(activity: Context) = SignInResult.FAILED

    override suspend fun signOut(activity: Context) = Unit
}
