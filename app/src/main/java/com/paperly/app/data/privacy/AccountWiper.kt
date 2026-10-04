package com.paperly.app.data.privacy

import android.content.Context
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.DeleteAccountResult
import com.paperly.app.domain.privacy.AccountStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Deletes the Firebase account; if Firebase refuses, signs out so the app stops acting as that account. */
@Singleton
class AccountWiper @Inject constructor(
    private val auth: AuthRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun deleteOrSignOut(): AccountStatus {
        if (auth.deleteAccount() == DeleteAccountResult.DELETED) return AccountStatus.DELETED
        auth.signOut(context) // the cloud data is already gone; failure here is reported as KEPT either way
        return AccountStatus.KEPT
    }
}
