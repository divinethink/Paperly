package com.paperly.app.data.sync.drive

import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.domain.auth.DriveAuth
import com.paperly.app.domain.auth.DriveToken
import com.paperly.app.domain.sync.CloudFile
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.CloudListing
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val DEFAULT_PAGE_SIZE = 100
private const val MAX_PAGES = 50

/**
 * Lists this app's files in the hidden appDataFolder. Only files carrying a valid `documentId` app property are
 * reported (anything else is not ours to touch). A listing that cannot be completed (error, too many pages) is
 * never returned half-way: the caller gets Failed and must conclude nothing.
 */
class DriveInventory(
    private val endpoints: DriveEndpoints,
    private val auth: DriveAuth,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : CloudInventory {

    override suspend fun listOwned(): CloudListing = when (val token = auth.token()) {
        is DriveToken.Ready -> listWith(token.value)
        is DriveToken.NeedsConsent -> CloudListing.NeedsConsent
        DriveToken.Unavailable -> CloudListing.Failed
    }

    private suspend fun listWith(token: String): CloudListing = try {
        CloudListing.Complete(io { listAll(token) })
    } catch (e: DriveException) {
        if (e.failure == DriveFailure.AUTH_EXPIRED) auth.invalidate(token)
        if (e.failure == DriveFailure.NEEDS_CONSENT) CloudListing.NeedsConsent else CloudListing.Failed
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        CloudListing.Failed
    }

    private fun listAll(token: String): List<CloudFile> {
        val files = ArrayList<CloudFile>()
        var pageToken: String? = null
        var pages = 0
        do {
            pages++
            if (pages > MAX_PAGES) throw DriveException(DriveFailure.RETRY, "too many files to list")
            pageToken = listPage(token, pageToken, files)
        } while (pageToken != null)
        return files
    }

    /** Adds one page to [into] and returns the next page token (null = that was the last page). */
    private fun listPage(token: String, pageToken: String?, into: MutableList<CloudFile>): String? {
        val fields = enc("nextPageToken,files(id,size,createdTime,appProperties)")
        val next = pageToken?.let { "&pageToken=" + enc(it) }.orEmpty()
        val url = "${endpoints.api}/files?spaces=appDataFolder&q=${enc("trashed=false")}" +
            "&fields=$fields&pageSize=$pageSize$next"
        val connection = openConnection(url, "GET", token)
        return exchange(connection) { code ->
            if (code != Http.OK) throw connection.failure(code)
            try {
                val json = JSONObject(connection.bodyText())
                val array = json.optJSONArray("files") ?: JSONArray()
                for (i in 0 until array.length()) cloudFileOf(array.getJSONObject(i))?.let(into::add)
                json.optString("nextPageToken").ifEmpty { null }
            } catch (e: JSONException) {
                throw DriveException(DriveFailure.RETRY, "unreadable response")
            }
        }
    }
}

private fun cloudFileOf(json: JSONObject): CloudFile? {
    val id = json.optString("id")
    val documentId = json.optJSONObject("appProperties")?.optString("documentId").orEmpty()
    if (id.isEmpty() || !SAFE_DOCUMENT_ID.matches(documentId)) return null
    val created = runCatching { Instant.parse(json.optString("createdTime")).toEpochMilli() }.getOrDefault(0L)
    return CloudFile(id, documentId, json.optString("size").toLongOrNull() ?: 0L, created)
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
