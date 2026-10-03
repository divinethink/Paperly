package com.paperly.app.data.sync

import com.paperly.app.domain.sync.ReadingMeta
import org.json.JSONObject

/** Firestore field names. Keep in sync with firestore.rules (validReading). */
internal object ReadingFields {
    const val DOCUMENT_ID = "documentId"
    const val LOCATOR = "locator"
    const val PROGRESS = "progressPercent"
    const val UPDATED_AT = "updatedAt"
    const val SCHEMA_VERSION = "schemaVersion"
}

private const val MAX_LOCATOR_LENGTH = 2000
private const val MAX_PAGE_DIGITS = 9
private val KEPT_LOCATIONS = listOf("progression", "position", "totalProgression")

internal fun ReadingMeta.toFirestoreMap(): Map<String, Any> = mapOf(
    ReadingFields.DOCUMENT_ID to documentId,
    ReadingFields.LOCATOR to locator,
    ReadingFields.PROGRESS to progressPercent.coerceIn(0f, 1f).toDouble(),
    ReadingFields.UPDATED_AT to updatedAt,
    ReadingFields.SCHEMA_VERSION to schemaVersion,
)

/** Defensive read: a missing or wrong-typed field yields null (skipped), never a crash. Id = Firestore document id. */
internal fun readingMetaFromMap(id: String, data: Map<String, Any?>): ReadingMeta? {
    val locator = (data[ReadingFields.LOCATOR] as? String)?.takeIf { it.isNotBlank() }
    val progress = (data[ReadingFields.PROGRESS] as? Number)?.toFloat()
    val updatedAt = (data[ReadingFields.UPDATED_AT] as? Number)?.toLong()
    return if (locator == null || progress == null || updatedAt == null) {
        null
    } else {
        ReadingMeta(
            documentId = id,
            locator = locator,
            progressPercent = progress.coerceIn(0f, 1f),
            updatedAt = updatedAt,
            schemaVersion = (data[ReadingFields.SCHEMA_VERSION] as? Number)?.toInt() ?: 1,
        )
    }
}

/**
 * The locator that may leave the device. PDF = a page number, kept as is. EPUB = Readium locator JSON, reduced
 * to href + type + progression/position: the `text` part (words around the position), chapter title and
 * fragments can hold book content and are dropped. null = not syncable (empty, unreadable, too long).
 */
internal fun syncableLocator(raw: String): String? {
    val value = raw.trim()
    return when {
        value.isEmpty() -> null
        value.all { it.isDigit() } -> value.takeIf { it.length <= MAX_PAGE_DIGITS }
        else -> epubLocatorWithoutText(value)
    }
}

private fun epubLocatorWithoutText(json: String): String? = runCatching {
    val source = JSONObject(json)
    val href = source.optString("href")
    val type = source.optString("type")
    val locations = JSONObject()
    val sourceLocations = source.optJSONObject("locations")
    KEPT_LOCATIONS.forEach { key -> sourceLocations?.opt(key)?.let { locations.put(key, it) } }
    val clean = JSONObject().put("href", href).put("locations", locations)
    if (type.isNotBlank()) clean.put("type", type)
    clean.toString().takeIf { href.isNotBlank() && it.length <= MAX_LOCATOR_LENGTH }
}.getOrNull()
