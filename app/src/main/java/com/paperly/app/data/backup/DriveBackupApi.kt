package com.paperly.app.data.backup

import com.paperly.app.data.sync.drive.DriveEndpoints
import com.paperly.app.data.sync.drive.DriveException
import com.paperly.app.data.sync.drive.DriveFailure
import com.paperly.app.data.sync.drive.Http
import com.paperly.app.data.sync.drive.bodyText
import com.paperly.app.data.sync.drive.exchange
import com.paperly.app.data.sync.drive.failure
import com.paperly.app.data.sync.drive.openConnection
import com.paperly.app.domain.backup.StoredBackup
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONObject

internal const val DRIVE_BACKUP_FOLDER = "Paperly Backup"

private const val FOLDER_MIME = "application/vnd.google-apps.folder"
private const val JSON_UTF8 = "application/json; charset=UTF-8"
private const val LIST_PAGE_SIZE = 100
private val SAFE_DRIVE_ID = Regex("[A-Za-z0-9_-]{1,200}")
private val BACKUP_NAME = Regex("Paperly-[A-Za-z]+\\.zip")

/**
 * Blocking Drive REST calls for the visible backup folder (callers run them on Dispatchers.IO). Sending and
 * downloading the file bytes reuse the generic chunk/media calls of DriveFileApi. With the drive.file scope
 * these queries only ever see what Paperly created itself.
 */
internal class DriveBackupApi(private val endpoints: DriveEndpoints) {

    /** The id of the "Paperly Backup" folder, or null if it does not exist (yet). */
    fun findFolder(token: String): String? {
        val query = enc("name='$DRIVE_BACKUP_FOLDER' and mimeType='$FOLDER_MIME' and trashed=false")
        val url = "${endpoints.api}/files?spaces=drive&q=$query&fields=${enc("files(id)")}&pageSize=1"
        val connection = openConnection(url, "GET", token)
        return exchange(connection) { code ->
            if (code != Http.OK) throw connection.failure(code)
            JSONObject(connection.bodyText()).optJSONArray("files")?.optJSONObject(0)?.optString("id")
                ?.takeIf { SAFE_DRIVE_ID.matches(it) }
        }
    }

    fun createFolder(token: String): String {
        val connection = openConnection("${endpoints.api}/files?fields=id", "POST", token)
        val body = """{"name":"$DRIVE_BACKUP_FOLDER","mimeType":"$FOLDER_MIME"}""".toByteArray(Charsets.UTF_8)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", JSON_UTF8)
        connection.setFixedLengthStreamingMode(body.size)
        return exchange(connection, write = { it.write(body) }) { code ->
            if (code != Http.OK) throw connection.failure(code)
            idOf(connection.bodyText())
        }
    }

    /** Paperly backup ZIPs inside [folderId], newest first. */
    fun listBackups(token: String, folderId: String): List<StoredBackup> {
        require(SAFE_DRIVE_ID.matches(folderId))
        val query = enc("'$folderId' in parents and trashed=false and mimeType!='$FOLDER_MIME'")
        val fields = enc("files(id,name,size,modifiedTime)")
        val url = "${endpoints.api}/files?spaces=drive&q=$query&fields=$fields&orderBy=modifiedTime%20desc" +
            "&pageSize=$LIST_PAGE_SIZE"
        val connection = openConnection(url, "GET", token)
        return exchange(connection) { code ->
            if (code != Http.OK) throw connection.failure(code)
            val files = JSONObject(connection.bodyText()).optJSONArray("files")
            val all = if (files == null) emptyList() else List(files.length()) { backupOf(files.getJSONObject(it)) }
            all.filterNotNull().filter { BACKUP_NAME.matches(it.name) }
        }
    }

    /** Starts a resumable upload of a new file named [name] inside [folderId]; returns the session URL (a secret). */
    fun createSession(token: String, folderId: String, name: String, size: Long): String {
        require(SAFE_DRIVE_ID.matches(folderId) && BACKUP_NAME.matches(name))
        val connection = openConnection("${endpoints.upload}/files?uploadType=resumable&fields=id", "POST", token)
        val body = """{"name":"$name","parents":["$folderId"]}""".toByteArray(Charsets.UTF_8)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", JSON_UTF8)
        connection.setRequestProperty("X-Upload-Content-Type", "application/zip")
        connection.setRequestProperty("X-Upload-Content-Length", size.toString())
        connection.setFixedLengthStreamingMode(body.size)
        return exchange(connection, write = { it.write(body) }) { code ->
            if (code != Http.OK) throw connection.failure(code)
            connection.getHeaderField("Location")?.takeIf { it.startsWith(endpoints.upload) }
                ?: throw DriveException(DriveFailure.RETRY, "no upload session")
        }
    }
}

private fun idOf(body: String): String =
    JSONObject(body).optString("id").takeIf { SAFE_DRIVE_ID.matches(it) }
        ?: throw DriveException(DriveFailure.RETRY, "response without id")

private fun backupOf(json: JSONObject): StoredBackup? {
    val id = json.optString("id")
    val name = json.optString("name")
    if (!SAFE_DRIVE_ID.matches(id) || name.isEmpty()) return null
    val modified = runCatching { Instant.parse(json.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0L)
    return StoredBackup(id, name, json.optString("size").toLongOrNull() ?: 0L, modified)
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
