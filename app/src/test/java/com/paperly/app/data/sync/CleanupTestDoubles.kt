package com.paperly.app.data.sync

import android.content.Context
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.auth.SignInResult
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.CloudListing
import com.paperly.app.domain.sync.DocumentMeta
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.RemoteDocument
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteResult
import com.paperly.app.domain.sync.SyncSettings
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class CleanupAuth(private val current: AuthState = AuthState.SignedIn("u1", null)) : AuthRepository {
    override val state: Flow<AuthState> = flowOf(current)

    override suspend fun signIn(activity: Context) = SignInResult.FAILED

    override suspend fun signOut(activity: Context) = Unit
}

internal class CleanupSettings(var isPaused: Boolean = false) : SyncSettings {
    override val wifiOnly: Flow<Boolean> = flowOf(true)
    override val paused: Flow<Boolean> get() = flowOf(isPaused)

    override suspend fun setWifiOnly(value: Boolean) = Unit

    override suspend fun setPaused(value: Boolean) = Unit
}

internal class CleanupInventory(var listing: CloudListing) : CloudInventory {
    override suspend fun listOwned() = listing
}

/** [ids] = what the cloud lists; [appearLater] = documents that show up between the listing and the final check. */
internal class CleanupRemote(var ids: Set<String>? = emptySet()) : RemoteMetadataStore {
    var appearLater: Set<String> = emptySet()

    override suspend fun putDocument(uid: String, meta: DocumentMeta, baseUpdatedAt: Long?) = RemoteResult.OK

    override suspend fun deleteDocument(uid: String, documentId: String) = RemoteResult.OK

    override suspend fun fetchDocument(uid: String, documentId: String): RemoteDocument =
        if (documentId in appearLater) {
            RemoteDocument.Found(DocumentMeta(documentId, "t", "pdf", 1, "c", createdAt = 1, updatedAt = 1))
        } else {
            RemoteDocument.Absent
        }

    override suspend fun fetchDocumentsSince(uid: String, updatedAfter: Long): List<DocumentMeta>? = emptyList()

    override suspend fun listDocumentIds(uid: String): Set<String>? = ids
}

internal class CleanupFiles(var deleteResult: RemoteResult = RemoteResult.OK) : RemoteFileStore {
    val deletes = mutableListOf<String>()

    override suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult =
        throw UnsupportedOperationException()

    override suspend fun download(documentId: String, sha256: String, target: DownloadTarget): FileSyncResult =
        throw UnsupportedOperationException()

    override suspend fun forgetUploads() = Unit

    override suspend fun delete(documentId: String): RemoteResult {
        deletes += documentId
        return deleteResult
    }
}
