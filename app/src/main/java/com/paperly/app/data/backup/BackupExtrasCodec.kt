package com.paperly.app.data.backup

import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import com.paperly.app.data.reader.AnnotationRects
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.scanner.MAX_PAGE_NOTE
import com.paperly.app.domain.scanner.MAX_PAGE_TITLE
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_LOCATOR = 8_192
private const val MAX_TEXT = 100_000
private val RECT_KEYS = listOf("rectLeft", "rectTop", "rectRight", "rectBottom")

/** Required, non-empty, bounded text; null when absent/empty/too long. */
private fun JSONObject.text(key: String, max: Int): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() && it.length <= max }

/** Optional text; null when absent/empty/too long. */
private fun JSONObject.optText(key: String): String? = text(key, MAX_TEXT)

private fun JSONObject.fraction(key: String): Float? =
    optDouble(key, Double.NaN).takeIf { it.isFinite() && it in 0.0..1.0 }?.toFloat()

/** The four page-fraction rect values (left, top, right, bottom) or null if any is missing/out of 0..1. */
private fun JSONObject.rect(): List<Float>? {
    val v = RECT_KEYS.map { fraction(it) }
    return if (v.all { it != null }) v.map { it ?: 0f } else null
}

private fun scanPageJson(p: BackupScanPage) = JSONObject().apply {
    put("documentId", p.documentId)
    put("pageIndex", p.pageIndex)
    put("title", p.title ?: JSONObject.NULL)
    put("note", p.note ?: JSONObject.NULL)
    put("updatedAt", p.updatedAt)
}

/** Needs a known document, a non-negative page index and at least one of title/note (within the app limits). */
private fun parseScanPage(o: JSONObject, docIds: Set<String>): BackupScanPage? {
    val docId = o.optString("documentId")
    val index = o.optInt("pageIndex", -1)
    val title = o.text("title", MAX_PAGE_TITLE)
    val note = o.text("note", MAX_PAGE_NOTE)
    val valid = docId in docIds && index >= 0 && (title != null || note != null)
    return if (valid) BackupScanPage(docId, index, title, note, o.optLong("updatedAt")) else null
}

/**
 * Tolerant reader/writer for the optional extras in the manifest (formatVersion stays 1: additive keys).
 * Entries that fail validation are dropped and counted, never crash; references to unknown documents are dropped.
 */
object BackupExtrasCodec {
    fun encode(extras: BackupExtras, root: JSONObject) {
        root.put("readingState", JSONArray(extras.readingState.map { readingJson(it) }))
        root.put("bookmarks", JSONArray(extras.bookmarks.map { bookmarkJson(it) }))
        root.put("annotations", JSONArray(extras.annotations.map { annotationJson(it) }))
        root.put("scanPages", JSONArray(extras.scanPages.map { scanPageJson(it) }))
    }

    /** Returns the valid extras for [documentIds] and the number of dropped entries. */
    fun decode(root: JSONObject, documentIds: Set<String>): Pair<BackupExtras, Int> {
        val (states, badStates) = parseList(root.optJSONArray("readingState")) { parseReading(it, documentIds) }
        val (marks, badMarks) = parseList(root.optJSONArray("bookmarks")) { parseBookmark(it, documentIds) }
        val (notes, badNotes) = parseList(root.optJSONArray("annotations")) { parseAnnotation(it, documentIds) }
        val (pages, badPages) = parseList(root.optJSONArray("scanPages")) { parseScanPage(it, documentIds) }
        return BackupExtras(states, marks, notes, pages) to (badStates + badMarks + badNotes + badPages)
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

    private fun readingJson(s: BackupReadingState) = JSONObject().apply {
        put("documentId", s.documentId)
        put("locator", s.locator)
        put("progressPercent", s.progressPercent.toDouble())
        put("lastOpenedAt", s.lastOpenedAt)
    }

    private fun bookmarkJson(b: BackupBookmark) = JSONObject().apply {
        put("bookmarkId", b.bookmarkId)
        put("documentId", b.documentId)
        put("locator", b.locator)
        put("title", b.title ?: JSONObject.NULL)
        put("createdAt", b.createdAt)
    }

    private fun annotationJson(a: BackupAnnotation) = JSONObject().apply {
        put("annotationId", a.annotationId)
        put("documentId", a.documentId)
        put("locator", a.locator)
        put("type", a.type)
        put("color", a.color ?: JSONObject.NULL)
        put("rectLeft", a.rectLeft.toDouble())
        put("rectTop", a.rectTop.toDouble())
        put("rectRight", a.rectRight.toDouble())
        put("rectBottom", a.rectBottom.toDouble())
        put("noteText", a.noteText ?: JSONObject.NULL)
        put("createdAt", a.createdAt)
        put("updatedAt", a.updatedAt)
        a.rects?.let { raw -> runCatching { JSONArray(raw) }.getOrNull()?.let { put("rects", it) } }
    }

    private fun parseReading(o: JSONObject, docIds: Set<String>): BackupReadingState? {
        val docId = o.optString("documentId")
        val locator = o.text("locator", MAX_LOCATOR)?.takeIf { SAFE_DOCUMENT_ID.matches(docId) && docId in docIds }
        val progress = o.optDouble("progressPercent", -1.0).toFloat()
        return if (locator != null && progress.isFinite() && progress >= 0f) {
            BackupReadingState(docId, locator, progress, o.optLong("lastOpenedAt"))
        } else {
            null
        }
    }

    private fun parseBookmark(o: JSONObject, docIds: Set<String>): BackupBookmark? {
        val id = o.optString("bookmarkId")
        val docId = o.optString("documentId")
        val ref = SAFE_DOCUMENT_ID.matches(id) && docId in docIds
        val locator = o.text("locator", MAX_LOCATOR)?.takeIf { ref }
        return if (locator != null) {
            BackupBookmark(id, docId, locator, o.optText("title"), o.optLong("createdAt"))
        } else {
            null
        }
    }

    private fun parseAnnotation(o: JSONObject, docIds: Set<String>): BackupAnnotation? {
        val id = o.optString("annotationId")
        val docId = o.optString("documentId")
        val ref = SAFE_DOCUMENT_ID.matches(id) && docId in docIds
        val type = AnnotationType.fromKey(o.optString("type"))
        val locator = o.text("locator", MAX_LOCATOR)?.takeIf { ref }
        val rect = o.rect()
        return if (type != null && locator != null && rect != null) {
            val created = o.optLong("createdAt")
            BackupAnnotation(
                annotationId = id,
                documentId = docId,
                locator = locator,
                type = type.key,
                color = if (o.isNull("color")) null else AnnotationColor.fromKey(o.optString("color"))?.key,
                rectLeft = rect[0],
                rectTop = rect[1],
                rectRight = rect[2],
                rectBottom = rect[3],
                noteText = o.optText("noteText"),
                createdAt = created,
                updatedAt = o.optLong("updatedAt", created),
                rects = AnnotationRects.normalize(o.optJSONArray("rects")?.toString()),
            )
        } else {
            null
        }
    }
}
