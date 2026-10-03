package com.paperly.app.data.sync.drive

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal object Http {
    const val OK = 200
    const val CREATED = 201
    const val NO_CONTENT = 204
    const val RESUME_INCOMPLETE = 308
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val REQUEST_TIMEOUT = 408
    const val GONE = 410
    const val TOO_MANY_REQUESTS = 429
    const val SERVER_ERROR = 500
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 60_000
}

/** What went wrong, in terms the sync logic cares about (never carries a token or a URL). */
internal enum class DriveFailure { AUTH_EXPIRED, NEEDS_CONSENT, RETRY, DENIED, NOT_FOUND, SESSION_LOST }

internal class DriveException(val failure: DriveFailure, message: String) : IOException(message)

// 403 is overloaded in the Drive API: the "reason" tells a busy server from a missing permission.
private val RETRY_REASONS = setOf(
    "rateLimitExceeded",
    "userRateLimitExceeded",
    "sharingRateLimitExceeded",
    "backendError",
    "storageQuotaExceeded", // the user's Drive is full: waits (long backoff) until space is freed
    "accessNotConfigured", // Drive API not enabled in the Google Cloud project yet: heals once the owner enables it
    "serviceDisabled",
)
private val CONSENT_REASONS = setOf("insufficientPermissions", "insufficientScopes", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")

internal fun failureFor(code: Int, errorBody: String): DriveFailure = when {
    code == Http.UNAUTHORIZED -> DriveFailure.AUTH_EXPIRED
    code == Http.NOT_FOUND || code == Http.GONE -> DriveFailure.NOT_FOUND
    code == Http.FORBIDDEN -> forbiddenFailure(errorBody)
    code == Http.REQUEST_TIMEOUT || code == Http.TOO_MANY_REQUESTS || code >= Http.SERVER_ERROR -> DriveFailure.RETRY
    else -> DriveFailure.DENIED
}

private fun forbiddenFailure(body: String): DriveFailure {
    val reason = runCatching {
        JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).getString("reason")
    }.getOrDefault("")
    return when (reason) {
        in RETRY_REASONS -> DriveFailure.RETRY
        in CONSENT_REASONS -> DriveFailure.NEEDS_CONSENT
        else -> DriveFailure.DENIED
    }
}

internal fun openConnection(url: String, method: String, token: String?): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = Http.CONNECT_TIMEOUT_MS
    connection.readTimeout = Http.READ_TIMEOUT_MS
    connection.instanceFollowRedirects = false // 308 means "resume incomplete" here, not a redirect
    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
    return connection
}

/**
 * One request/response round trip. [write] sends the body (set doOutput and the fixed length first); [handle]
 * turns the status code into a result and may throw [DriveException]. Network trouble becomes a RETRY failure.
 * The connection is closed afterwards unless [keepOpenOnSuccess] (a download stream closes it itself).
 */
internal fun <T> exchange(
    connection: HttpURLConnection,
    write: ((OutputStream) -> Unit)? = null,
    keepOpenOnSuccess: Boolean = false,
    handle: (Int) -> T,
): T {
    var succeeded = false
    try {
        if (write != null) connection.outputStream.use(write)
        return handle(connection.responseCode).also { succeeded = true }
    } catch (e: DriveException) {
        throw e
    } catch (e: IOException) {
        throw DriveException(DriveFailure.RETRY, "network: ${e.javaClass.simpleName}")
    } finally {
        if (!(succeeded && keepOpenOnSuccess)) connection.disconnect()
    }
}

internal fun HttpURLConnection.bodyText(): String =
    inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

internal fun HttpURLConnection.failure(code: Int): DriveException {
    val body = runCatching { errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull().orEmpty()
    return DriveException(failureFor(code, body), "http $code")
}
