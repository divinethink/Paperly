package com.paperly.app.data.backup

import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.core.model.DocumentType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Bump only for breaking manifest changes; additive fields never need a bump (decoder ignores unknown keys). */
const val BACKUP_FORMAT_VERSION = 1

data class BackupFolder(
    val folderId: String,
    val name: String,
    val parentFolderId: String?,
    val createdAt: Long,
)

data class BackupDocument(
    val documentId: String,
    val title: String,
    val type: String,
    val sizeBytes: Long,
    val checksum: String, // SHA-256 hex of the file bytes; also used to verify the archive entry
    val folderId: String?,
    val tags: List<String>,
    val isFavorite: Boolean,
    val createdAt: Long, // UTC epoch millis
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

data class BackupManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val folders: List<BackupFolder>,
    val documents: List<BackupDocument>,
    /** Optional (absent in older archives): reading progress, bookmarks, annotations. */
    val extras: BackupExtras = BackupExtras.EMPTY,
    /** Extras entries dropped by validation while decoding (never encoded; does not affect archive verify). */
    val invalidExtras: Int = 0,
    /** Entries dropped by validation while decoding (never encoded). */
    val invalidEntries: Int = 0,
)

/** Tolerant reader: unknown keys ignored, invalid entries dropped and counted, never crashes on bad input. */
object BackupManifestCodec {
    private val sha256Hex = Regex("[0-9a-f]{64}")

    fun encode(m: BackupManifest): String = JSONObject().apply {
        put("formatVersion", m.formatVersion)
        put("schemaVersion", m.schemaVersion)
        put("appVersion", m.appVersion)
        put("createdAt", m.createdAt)
        put("folders", JSONArray(m.folders.map { folderJson(it) }))
        put("documents", JSONArray(m.documents.map { documentJson(it) }))
        BackupExtrasCodec.encode(m.extras, this)
    }.toString()

    fun decode(json: String): BackupManifest? = try {
        val root = JSONObject(json)
        val (folders, badFolders) = parseList(root.optJSONArray("folders"), { parseFolder(it) })
        val (docs, badDocs) = parseList(root.optJSONArray("documents"), { parseDocument(it) })
        val (extras, badExtras) = BackupExtrasCodec.decode(root, docs.map { it.documentId }.toSet())
        BackupManifest(
            formatVersion = root.getInt("formatVersion"),
            schemaVersion = root.optInt("schemaVersion"),
            appVersion = root.optString("appVersion"),
            createdAt = root.optLong("createdAt"),
            folders = folders,
            documents = docs,
            extras = extras,
            invalidExtras = badExtras,
            invalidEntries = badFolders + badDocs,
        )
    } catch (e: JSONException) {
        null
    }

    private fun <T : Any> parseList(arr: JSONArray?, parse: (JSONObject) -> T?): Pair<List<T>, Int> {
        val ok = ArrayList<T>()
        var bad = 0
        for (i in 0 until (arr?.length() ?: 0)) {
            val item = arr?.optJSONObject(i)?.let(parse)
            if (item != null) ok.add(item) else bad++
        }
        return ok to bad
    }

    private fun folderJson(f: BackupFolder) = JSONObject().apply {
        put("folderId", f.folderId)
        put("name", f.name)
        put("parentFolderId", f.parentFolderId ?: JSONObject.NULL)
        put("createdAt", f.createdAt)
    }

    private fun documentJson(d: BackupDocument) = JSONObject().apply {
        put("documentId", d.documentId)
        put("title", d.title)
        put("type", d.type)
        put("sizeBytes", d.sizeBytes)
        put("checksum", d.checksum)
        put("folderId", d.folderId ?: JSONObject.NULL)
        put("tags", JSONArray(d.tags))
        put("isFavorite", d.isFavorite)
        put("createdAt", d.createdAt)
        put("updatedAt", d.updatedAt)
        put("lastOpenedAt", d.lastOpenedAt ?: JSONObject.NULL)
    }

    private fun parseFolder(o: JSONObject): BackupFolder? {
        val id = o.optString("folderId")
        val name = o.optString("name")
        return if (SAFE_DOCUMENT_ID.matches(id) && name.isNotBlank()) {
            BackupFolder(id, name, o.nullableString("parentFolderId"), o.optLong("createdAt"))
        } else {
            null
        }
    }

    private fun parseDocument(o: JSONObject): BackupDocument? {
        val id = o.optString("documentId")
        val title = o.optString("title")
        val type = o.optString("type")
        val checksum = o.optString("checksum")
        val valid = SAFE_DOCUMENT_ID.matches(id) && title.isNotBlank() && DocumentType.isValid(type) &&
            sha256Hex.matches(checksum) && o.optLong("sizeBytes", -1L) >= 0
        return if (valid) {
            val created = o.optLong("createdAt")
            BackupDocument(
                documentId = id,
                title = title,
                type = type,
                sizeBytes = o.getLong("sizeBytes"),
                checksum = checksum,
                folderId = o.nullableString("folderId"),
                tags = o.optJSONArray("tags")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty(),
                isFavorite = o.optBoolean("isFavorite"),
                createdAt = created,
                updatedAt = o.optLong("updatedAt", created),
                lastOpenedAt = if (o.isNull("lastOpenedAt")) null else o.optLong("lastOpenedAt"),
            )
        } else {
            null
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
