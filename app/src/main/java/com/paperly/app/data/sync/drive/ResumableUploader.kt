package com.paperly.app.data.sync.drive

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val MAX_STALLS = 3

/** Where an interrupted upload stands after asking Drive. */
private sealed interface Resume {
    class Finished(val fileId: String) : Resume

    class At(val url: String, val offset: Long) : Resume
}

/**
 * Sends one file to Drive in chunks and returns its Drive id. The session is saved first, so after a crash or a
 * dropped connection the next call asks Drive how far it got and continues from there.
 */
class ResumableUploader(private val api: DriveFileApi, private val sessions: UploadSessionStore) {

    suspend fun upload(token: String, documentId: String, file: File, sha256: String): String {
        val size = file.length()
        return when (val resumed = resume(documentId, sha256, size)) {
            is Resume.Finished -> resumed.fileId
            is Resume.At -> pump(documentId, resumed.url, file, resumed.offset)
            null -> {
                val url = io { api.createSession(token, documentId, sha256, size) }
                sessions.save(documentId, UploadSession(url, sha256, size))
                pump(documentId, url, file, 0L)
            }
        }
    }

    private suspend fun resume(documentId: String, sha256: String, size: Long): Resume? {
        val saved = sessions.get(documentId)?.takeIf { it.sha256 == sha256 && it.size == size } ?: return null
        return try {
            when (val progress = io { api.queryProgress(saved.url, size) }) {
                is ChunkResult.Complete -> Resume.Finished(progress.file.id)
                is ChunkResult.Continue -> Resume.At(saved.url, progress.next)
            }
        } catch (e: DriveException) {
            if (e.failure != DriveFailure.SESSION_LOST) throw e
            sessions.clear(documentId) // expired: start a new session
            null
        }
    }

    /** Sends chunks until Drive reports the file complete. Cancellation is honoured between chunks. */
    private suspend fun pump(documentId: String, url: String, file: File, start: Long): String {
        val size = file.length()
        var offset = start
        var stalls = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val result = sendChunk(documentId, url, file, offset)) {
                is ChunkResult.Complete -> return result.file.id
                is ChunkResult.Continue -> {
                    stalls = if (result.next > offset) 0 else stalls + 1
                    if (stalls >= MAX_STALLS || result.next >= size) throw DriveException(DriveFailure.RETRY, "stuck")
                    offset = result.next
                }
            }
        }
    }

    private suspend fun sendChunk(documentId: String, url: String, file: File, offset: Long): ChunkResult = try {
        io { api.putChunk(url, file, offset, file.length()) }
    } catch (e: DriveException) {
        if (e.failure != DriveFailure.SESSION_LOST) throw e
        sessions.clear(documentId)
        throw DriveException(DriveFailure.RETRY, "upload session lost")
    }
}
