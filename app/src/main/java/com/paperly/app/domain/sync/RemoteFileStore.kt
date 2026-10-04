package com.paperly.app.domain.sync

import java.io.File
import java.io.InputStream

/** Result of moving one file to/from the cloud. */
sealed interface FileSyncResult {
    /** [cloudRef] = the Drive file id. */
    data class Done(val cloudRef: String) : FileSyncResult

    /** Transient (offline, timeout, server busy, checksum mismatch): try again later. */
    data object Retry : FileSyncResult

    /** Rejected for good (bad input, missing local file, permission refused). */
    data object Denied : FileSyncResult

    /** The user has to allow Drive access first; nothing was attempted. */
    data object NeedsConsent : FileSyncResult

    /** Download only: there is no cloud copy. */
    data object NotFound : FileSyncResult
}

/** Where a downloaded file goes. Nothing is kept unless [store] returns the checksum that was expected. */
interface DownloadTarget {
    /** Writes [input] and returns the SHA-256 (lowercase hex) of what was stored. */
    suspend fun store(input: InputStream): String

    /** Removes whatever [store] wrote (called when the checksum did not match). */
    suspend fun discard()
}

/** Cloud file storage (Drive appDataFolder). Every call is idempotent and verified by checksum. */
interface RemoteFileStore {
    /**
     * Uploads [file] (immutable original) unless an identical copy is already there. Resumable: an interrupted
     * upload continues from the last confirmed byte on the next call. [sha256] = lowercase hex of the file.
     */
    suspend fun upload(documentId: String, file: File, sha256: String): FileSyncResult

    /** Downloads the copy of [documentId] into [target]; a checksum mismatch discards it and returns Retry. */
    suspend fun download(documentId: String, sha256: String, target: DownloadTarget): FileSyncResult

    /** The signed-in account changed: drop interrupted-upload state that belongs to the previous account. */
    suspend fun forgetUploads()

    /**
     * Removes every cloud copy of [documentId] (found by its `documentId` app property, so no stored Drive id is
     * needed) and any interrupted upload of it. Idempotent: no copy at all counts as done (OK).
     * RETRY = transient, DEFERRED = Drive access not allowed yet, DENIED = rejected for good.
     */
    suspend fun delete(documentId: String): RemoteResult
}
