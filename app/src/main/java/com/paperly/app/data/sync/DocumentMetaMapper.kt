package com.paperly.app.data.sync

import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.domain.sync.DocumentMeta

/** Firestore field names. Keep in sync with firestore.rules (validDocument). */
internal object MetaFields {
    const val DOCUMENT_ID = "documentId"
    const val TITLE = "title"
    const val TYPE = "type"
    const val SIZE_BYTES = "sizeBytes"
    const val CHECKSUM = "checksum"
    const val FOLDER_ID = "folderId"
    const val TAGS = "tags"
    const val IS_FAVORITE = "isFavorite"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val DELETED_AT = "deletedAt"
    const val SCHEMA_VERSION = "schemaVersion"
}

/** Optional fields are omitted when null (the rules accept absent or null). */
internal fun DocumentMeta.toFirestoreMap(): Map<String, Any> = buildMap {
    put(MetaFields.DOCUMENT_ID, documentId)
    put(MetaFields.TITLE, title)
    put(MetaFields.TYPE, type)
    put(MetaFields.SIZE_BYTES, sizeBytes)
    put(MetaFields.CHECKSUM, checksum)
    put(MetaFields.IS_FAVORITE, isFavorite)
    put(MetaFields.CREATED_AT, createdAt)
    put(MetaFields.UPDATED_AT, updatedAt)
    put(MetaFields.SCHEMA_VERSION, schemaVersion)
    folderId?.let { put(MetaFields.FOLDER_ID, it) }
    tags?.let { put(MetaFields.TAGS, it) }
    deletedAt?.let { put(MetaFields.DELETED_AT, it) }
}

/**
 * Defensive read: a document missing a required field or with a wrong type yields null (skipped), never a crash
 * or a half-filled object. The id comes from the Firestore document id, not from the payload.
 */
internal fun documentMetaFromMap(id: String, data: Map<String, Any?>): DocumentMeta? {
    val title = data[MetaFields.TITLE] as? String
    val type = data[MetaFields.TYPE] as? String
    val checksum = data[MetaFields.CHECKSUM] as? String
    val sizeBytes = (data[MetaFields.SIZE_BYTES] as? Number)?.toLong()
    val createdAt = (data[MetaFields.CREATED_AT] as? Number)?.toLong()
    val updatedAt = (data[MetaFields.UPDATED_AT] as? Number)?.toLong()
    val required = listOf(title, type, checksum, sizeBytes, createdAt, updatedAt)
    return if (required.any { it == null }) {
        null
    } else {
        DocumentMeta(
            documentId = id,
            title = title.orEmpty(),
            type = type.orEmpty(),
            sizeBytes = sizeBytes ?: 0L,
            checksum = checksum.orEmpty(),
            folderId = data[MetaFields.FOLDER_ID] as? String,
            tags = (data[MetaFields.TAGS] as? List<*>)?.filterIsInstance<String>(),
            isFavorite = data[MetaFields.IS_FAVORITE] as? Boolean ?: false,
            createdAt = createdAt ?: 0L,
            updatedAt = updatedAt ?: 0L,
            deletedAt = (data[MetaFields.DELETED_AT] as? Number)?.toLong(),
            schemaVersion = (data[MetaFields.SCHEMA_VERSION] as? Number)?.toInt() ?: 1,
        )
    }
}

/** Only the syncable part of a row: never the local path, cloud ref or unused/sensitive columns. */
internal fun DocumentEntity.toMeta(): DocumentMeta = DocumentMeta(
    documentId = documentId,
    title = title,
    type = type,
    sizeBytes = sizeBytes,
    checksum = checksum,
    folderId = folderId,
    tags = tags,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    schemaVersion = schemaVersion,
)
