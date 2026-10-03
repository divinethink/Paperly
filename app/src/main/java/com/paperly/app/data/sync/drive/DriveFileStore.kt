package com.paperly.app.data.sync.drive

import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.sync.DownloadTarget
import com.paperly.app.domain.sync.FileSyncResult
import com.paperly.app.domain.sync.RemoteFileStore
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/**
 * Drive appDataFolder as cloud file storage. Originals are immutable, so a file is uploaded at most once per
 * account: an identical copy already in Drive (found by the `documentId` app property) is adopted, never
 * uploaded twice. Every upload is verified against the expected SHA-256 before it counts as done.
 */
@Singleton
class DriveFileStore @Inject constructor(
    private val api: DriveFileApi,
    private val auth: DriveAuth,
    private val sessions: UploadSessionStore,
) : RemoteFileStore {
    private val uploader = ResumableUploader(api, sessions)

    override suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult {
        val valid = SAFE_DOCUMENT_ID.matches(documentId) && SHA256_HEX.matches(sha256) &&
            file.isFile && file.length() > 0
        return if (valid) withToken { uploadWith(it, documentId, file, sha256) } else FileSyncResult.Denied
    }

    override suspend fun download(documentId: String, sha256: String, target: DownloadTarget): FileSyncResult {
        val valid = SAFE_DOCUMENT_ID.matches(documentId) && SHA256_HEX.matches(sha256)
        return if (valid) withToken { downloadWith(it, documentId, sha256, target) } else FileSyncResult.Denied
    }

    override suspend fun forgetUploads() = sessions.clearAll()

    private suspend fun uploadWith(token: String, documentId: String, file: File, sha256: String): FileSyncResult {
        // The local file must still be what the library says it is; never upload something corrupted.
        if (!io { digestOf(file, "SHA-256") }.equals(sha256, ignoreCase = true)) return FileSyncResult.Denied
        val existing = adoptExisting(token, documentId, file, sha256)
        val fileId = existing ?: uploader.upload(token, documentId, file, sha256)
        return if (existing != null || verified(token, fileId, file, sha256)) {
            sessions.clear(documentId)
            FileSyncResult.Done(fileId)
        } else {
            sessions.clear(documentId)
            io { api.deleteFile(token, fileId) } // a copy that does not match must not stay
            FileSyncResult.Retry
        }
    }

    /** Id of an identical copy that is already in Drive; any other copy with this document id is removed. */
    private suspend fun adoptExisting(token: String, documentId: String, file: File, sha256: String): String? {
        val found = io { api.findByDocumentId(token, documentId) }
        val good = found.firstOrNull { it.matches(file.length(), sha256) { md5Of(file) } }
        found.filter { it !== good }.forEach { io { api.deleteFile(token, it.id) } }
        return good?.id
    }

    private suspend fun verified(token: String, fileId: String, file: File, sha256: String): Boolean {
        val remote = io { api.getFile(token, fileId) }
        return remote.matches(file.length(), sha256) { md5Of(file) }
    }

    private suspend fun downloadWith(
        token: String,
        documentId: String,
        sha256: String,
        target: DownloadTarget,
    ): FileSyncResult {
        val remote = io { api.findByDocumentId(token, documentId) }.firstOrNull() ?: return FileSyncResult.NotFound
        val stored = io { api.openMedia(token, remote.id).use { target.store(it) } }
        if (stored.equals(sha256, ignoreCase = true)) return FileSyncResult.Done(remote.id)
        target.discard() // corrupted in transit: nothing from this attempt is kept
        return FileSyncResult.Retry
    }

    private suspend fun withToken(block: suspend (String) -> FileSyncResult): FileSyncResult =
        when (val token = auth.token()) {
            is DriveToken.Ready -> guarded(token.value, block)
            is DriveToken.NeedsConsent -> FileSyncResult.NeedsConsent
            DriveToken.Unavailable -> FileSyncResult.Retry
        }

    private suspend fun guarded(token: String, block: suspend (String) -> FileSyncResult): FileSyncResult = try {
        block(token)
    } catch (e: DriveException) {
        when (e.failure) {
            DriveFailure.AUTH_EXPIRED -> {
                auth.invalidate(token)
                FileSyncResult.Retry
            }
            DriveFailure.NEEDS_CONSENT -> FileSyncResult.NeedsConsent
            DriveFailure.DENIED -> FileSyncResult.Denied
            DriveFailure.NOT_FOUND, DriveFailure.SESSION_LOST, DriveFailure.RETRY -> FileSyncResult.Retry
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        FileSyncResult.Retry
    }
}

private fun md5Of(file: File): String = digestOf(file, "MD5")
