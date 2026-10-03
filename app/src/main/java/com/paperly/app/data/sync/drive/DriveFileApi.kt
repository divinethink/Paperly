package com.paperly.app.data.sync.drive

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URLEncoder
import org.json.JSONObject

private const val CHUNK_BYTES = 16L * 256 * 1024 // Drive wants multiples of 256 KiB (except the last chunk)
private const val COPY_BUFFER = 64 * 1024
private const val LIST_PAGE_SIZE = 10
private const val JSON_UTF8 = "application/json; charset=UTF-8"

/**
 * Blocking Drive REST calls (callers run them on Dispatchers.IO). Everything lives in the hidden appDataFolder;
 * a file is found again by its `documentId` app property, so no Drive id has to be stored anywhere to be safe.
 * Inputs (document id, sha256) must already be validated by the caller: they go into a JSON body and a query.
 */
class DriveFileApi(private val endpoints: DriveEndpoints) {

    fun findByDocumentId(token: String, documentId: String): List<DriveFile> {
        val query = enc("appProperties has { key='documentId' and value='$documentId' } and trashed=false")
        val fields = enc("files(id,size,sha256Checksum,md5Checksum)")
        val url = "${endpoints.api}/files?spaces=appDataFolder&q=$query&fields=$fields&pageSize=$LIST_PAGE_SIZE"
        val connection = openConnection(url, "GET", token)
        return exchange(connection) { code ->
            if (code != Http.OK) throw connection.failure(code)
            val files = JSONObject(connection.bodyText()).optJSONArray("files")
            if (files == null) emptyList() else List(files.length()) { driveFileOf(files.getJSONObject(it)) }
        }
    }

    /** Starts a resumable upload and returns its session URL (valid ~1 week; it is a secret, never log it). */
    fun createSession(token: String, documentId: String, sha256: String, size: Long): String {
        val connection = openConnection("${endpoints.upload}/files?uploadType=resumable&fields=id", "POST", token)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", JSON_UTF8)
        connection.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
        connection.setRequestProperty("X-Upload-Content-Length", size.toString())
        val body = metadataJson(documentId, sha256).toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        return exchange(connection, write = { it.write(body) }) { code ->
            if (code != Http.OK) throw connection.failure(code)
            connection.getHeaderField("Location")?.takeIf { it.startsWith(endpoints.upload) }
                ?: throw DriveException(DriveFailure.RETRY, "no upload session")
        }
    }

    /** Asks Drive how much of the upload it has. */
    fun queryProgress(sessionUrl: String, total: Long): ChunkResult {
        val connection = sessionConnection(sessionUrl, "bytes */$total")
        connection.setFixedLengthStreamingMode(0)
        return exchange(connection, write = {}) { chunkResult(connection, it) }
    }

    /** Sends the next chunk, starting at [offset] (at most [CHUNK_BYTES]). */
    fun putChunk(sessionUrl: String, source: File, offset: Long, total: Long): ChunkResult {
        val length = minOf(CHUNK_BYTES, total - offset)
        val connection = sessionConnection(sessionUrl, "bytes $offset-${offset + length - 1}/$total")
        connection.setFixedLengthStreamingMode(length)
        return exchange(connection, write = { copyRange(source, offset, length, it) }) { chunkResult(connection, it) }
    }

    fun getFile(token: String, fileId: String): DriveFile {
        val fields = enc("id,size,sha256Checksum,md5Checksum")
        val connection = openConnection("${endpoints.api}/files/${enc(fileId)}?fields=$fields", "GET", token)
        return exchange(connection) { code ->
            if (code != Http.OK) throw connection.failure(code)
            parseDriveFile(connection.bodyText())
        }
    }

    /** Deleting a file that is already gone counts as done. */
    fun deleteFile(token: String, fileId: String) {
        val connection = openConnection("${endpoints.api}/files/${enc(fileId)}", "DELETE", token)
        exchange(connection) { code ->
            if (code != Http.NO_CONTENT && code != Http.NOT_FOUND) throw connection.failure(code)
        }
    }

    /** Streams the file content. The caller must close the stream (that also closes the connection). */
    fun openMedia(token: String, fileId: String): InputStream {
        val connection = openConnection("${endpoints.api}/files/${enc(fileId)}?alt=media", "GET", token)
        return exchange(connection, keepOpenOnSuccess = true) { code ->
            if (code != Http.OK) throw connection.failure(code)
            ConnectionStream(connection, connection.inputStream)
        }
    }

    // The session URL is itself the credential, so no Authorization header goes to it (the token may have expired).
    private fun sessionConnection(sessionUrl: String, contentRange: String): HttpURLConnection {
        if (!sessionUrl.startsWith(endpoints.upload)) throw DriveException(DriveFailure.RETRY, "foreign session url")
        val connection = openConnection(sessionUrl, "PUT", null)
        connection.doOutput = true
        connection.setRequestProperty("Content-Range", contentRange)
        return connection
    }

    private fun chunkResult(connection: HttpURLConnection, code: Int): ChunkResult = when (code) {
        Http.OK, Http.CREATED -> ChunkResult.Complete(parseDriveFile(connection.bodyText()))
        Http.RESUME_INCOMPLETE -> ChunkResult.Continue(nextOffsetFrom(connection.getHeaderField("Range")))
        Http.NOT_FOUND, Http.GONE -> throw DriveException(DriveFailure.SESSION_LOST, "upload session lost")
        else -> throw connection.failure(code)
    }

    // Ids are validated by the caller (letters, digits, '_' and '-' only; hex), so nothing needs JSON escaping.
    private fun metadataJson(documentId: String, sha256: String): String =
        """{"name":"$documentId","parents":["appDataFolder"],""" +
            """"appProperties":{"documentId":"$documentId","sha256":"$sha256"}}"""
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun copyRange(source: File, offset: Long, length: Long, out: OutputStream) {
    RandomAccessFile(source, "r").use { file ->
        file.seek(offset)
        val buffer = ByteArray(COPY_BUFFER)
        var remaining = length
        while (remaining > 0) {
            val n = file.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) throw IOException("file is shorter than expected")
            out.write(buffer, 0, n)
            remaining -= n
        }
    }
}

private class ConnectionStream(private val connection: HttpURLConnection, input: InputStream) :
    FilterInputStream(input) {
    override fun close() {
        try {
            super.close()
        } finally {
            connection.disconnect()
        }
    }
}
