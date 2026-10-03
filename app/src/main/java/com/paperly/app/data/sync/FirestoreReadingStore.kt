package com.paperly.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.paperly.app.data.auth.await
import com.paperly.app.domain.sync.ReadingMeta
import com.paperly.app.domain.sync.ReadingPullDecision
import com.paperly.app.domain.sync.RemoteReadingStore
import com.paperly.app.domain.sync.RemoteResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Layout: users/{uid}/readingState/{documentId}. Put = one transaction (read cloud version, write only if ours is
 * not older), so an old device can never move the position backwards. Offline the transaction fails -> RETRY.
 */
@Singleton
class FirestoreReadingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RemoteReadingStore {

    override suspend fun putReading(uid: String, meta: ReadingMeta): RemoteResult = call {
        val ref = readings(uid).document(meta.documentId)
        FirebaseFirestore.getInstance().runTransaction(
            Transaction.Function { tx ->
                val snapshot = tx.get(ref)
                val remote = if (snapshot.exists()) {
                    (snapshot.get(ReadingFields.UPDATED_AT) as? Number)?.toLong()
                } else {
                    null
                }
                val allowed = ReadingPullDecision.shouldPush(remote, meta.updatedAt)
                if (allowed) tx.set(ref, meta.toFirestoreMap())
                allowed
            },
        ).await()
        RemoteResult.OK
    }

    override suspend fun deleteReading(uid: String, documentId: String): RemoteResult = call {
        readings(uid).document(documentId).delete().await()
        RemoteResult.OK
    }

    private fun readings(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("readingState")

    // Without google-services.json there is no FirebaseApp and getInstance() would throw.
    private fun configured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private suspend fun call(block: suspend () -> RemoteResult): RemoteResult = try {
        if (!configured()) {
            RemoteResult.RETRY
        } else {
            withTimeout(TIMEOUT_MS) { block() }
        }
    } catch (e: TimeoutCancellationException) {
        RemoteResult.RETRY
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) RemoteResult.DENIED else RemoteResult.RETRY
    } catch (e: Exception) {
        RemoteResult.RETRY
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadingRemoteModule {
    @Binds
    abstract fun bindRemoteReadingStore(impl: FirestoreReadingStore): RemoteReadingStore
}
