package com.paperly.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.paperly.app.data.auth.await
import com.paperly.app.domain.sync.ConflictCheck
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteDocument
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
 * Layout: users/{uid}/documents/{documentId}. Writes are `set` on a fixed id, so retries and double-taps are
 * idempotent. Every call has a timeout: Firestore's write task never completes while offline.
 */
@Singleton
class FirestoreMetadataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RemoteMetadataStore {

    /**
     * One transaction: read the cloud copy, let [ConflictCheck] decide, write only if allowed. A transaction needs
     * the network, so offline it fails -> RETRY (never a silent local-only write).
     */
    override suspend fun putDocument(uid: String, meta: DocumentMeta, baseUpdatedAt: Long?): RemoteResult = call {
        val ref = documents(uid).document(meta.documentId)
        val written = FirebaseFirestore.getInstance().runTransaction(
            Transaction.Function { tx ->
                val snapshot = tx.get(ref)
                val remote = if (snapshot.exists()) {
                    (snapshot.get(MetaFields.UPDATED_AT) as? Number)?.toLong() ?: Long.MAX_VALUE // unreadable = doubt
                } else {
                    null
                }
                val allowed = ConflictCheck.canWrite(baseUpdatedAt, remote, meta.updatedAt)
                if (allowed) tx.set(ref, meta.toFirestoreMap())
                allowed
            },
        ).await()
        if (written) RemoteResult.OK else RemoteResult.CONFLICT
    }

    override suspend fun deleteDocument(uid: String, documentId: String): RemoteResult = call {
        documents(uid).document(documentId).delete().await()
        RemoteResult.OK
    }

    override suspend fun fetchDocument(uid: String, documentId: String): RemoteDocument = try {
        if (!configured()) {
            RemoteDocument.Failed
        } else {
            withTimeout(TIMEOUT_MS) {
                val snapshot = documents(uid).document(documentId).get(Source.SERVER).await()
                if (!snapshot.exists()) {
                    RemoteDocument.Absent
                } else {
                    documentMetaFromMap(snapshot.id, snapshot.data.orEmpty())?.let { RemoteDocument.Found(it) }
                        ?: RemoteDocument.Failed
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        RemoteDocument.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RemoteDocument.Failed
    }

    override suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>? = try {
        if (!configured()) {
            null
        } else {
            withTimeout(TIMEOUT_MS) {
                val snapshot = documents(uid)
                    .whereGreaterThan(MetaFields.UPDATED_AT, updatedAfter)
                    .get(Source.SERVER) // server truth: an offline cache must not look like "nothing new"
                    .await()
                snapshot.documents.mapNotNull { documentMetaFromMap(it.id, it.data.orEmpty()) }
            }
        }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun documents(uid: String) =
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("documents")

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
abstract class SyncRemoteModule {
    @Binds
    abstract fun bindRemoteMetadataStore(impl: FirestoreMetadataStore): RemoteMetadataStore
}
