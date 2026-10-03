package com.paperly.app.data.sync.drive

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class DriveEndpoints(val api: String, val upload: String) {
    companion object {
        val PRODUCTION = DriveEndpoints(
            api = "https://www.googleapis.com/drive/v3",
            upload = "https://www.googleapis.com/upload/drive/v3",
        )
    }
}

/** The fields of a Drive file that sync cares about. A hash Drive has not computed (yet) is null. */
class DriveFile(val id: String, val size: Long?, val sha256: String?, val md5: String?)

sealed interface ChunkResult {
    /** Drive has confirmed bytes up to [next] (exclusive); continue from there. */
    class Continue(val next: Long) : ChunkResult

    class Complete(val file: DriveFile) : ChunkResult
}

private const val DIGEST_BUFFER = 64 * 1024

internal fun driveFileOf(json: JSONObject): DriveFile {
    val id = json.optString("id")
    if (id.isEmpty()) throw DriveException(DriveFailure.RETRY, "response without id")
    return DriveFile(
        id = id,
        size = json.optString("size").toLongOrNull(),
        sha256 = json.optString("sha256Checksum").ifEmpty { null },
        md5 = json.optString("md5Checksum").ifEmpty { null },
    )
}

internal fun parseDriveFile(body: String): DriveFile = try {
    driveFileOf(JSONObject(body))
} catch (e: JSONException) {
    throw DriveException(DriveFailure.RETRY, "unreadable response")
}

/** Drive's `Range: bytes=0-42` header (bytes received so far) -> next offset 43; no header = nothing received. */
internal fun nextOffsetFrom(range: String?): Long {
    if (range == null) return 0L
    val last = range.substringAfterLast('-', "").trim().toLongOrNull()
        ?: throw DriveException(DriveFailure.RETRY, "unreadable range")
    return last + 1
}

/** Lowercase hex digest of a whole file (streamed). */
internal fun digestOf(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().use { input ->
        val buffer = ByteArray(DIGEST_BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Is this remote copy the file we expect? Size first, then SHA-256 if Drive reports one, else MD5 (computed lazily
 * by [md5]). If Drive reports no hash at all only the size can be compared.
 */
internal fun DriveFile.matches(size: Long, sha256: String, md5: () -> String): Boolean = when {
    this.size != null && this.size != size -> false
    this.sha256 != null -> this.sha256.equals(sha256, ignoreCase = true)
    this.md5 != null -> this.md5.equals(md5(), ignoreCase = true)
    else -> true
}

/** Blocking Drive calls run on the IO dispatcher. */
internal suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
