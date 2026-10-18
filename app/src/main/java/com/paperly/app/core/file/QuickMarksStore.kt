package com.paperly.app.core.file

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Folder (under `filesDir`) of remembered quick-view EPUB marks; "Delete all my data" empties it. */
const val QUICK_MARKS_DIR = "quick-marks"

private const val VERSION = 1
private const val MAX_FILES = 200
private const val EXT = "json"
private val CHECKSUM = Regex("[0-9a-f]{64}")

/** What is remembered about one book opened from outside: its marks, bookmarks and reading position (Locator JSON). */
data class QuickMarks(val annotations: List<EpubAnnotation>, val bookmarks: List<String>, val locator: String?)

/**
 * Marks of an EPUB opened without importing (Open-With), kept in the app's private folder under the file's SHA-256:
 * opening the same file again brings them back, like Moon+ Reader. The book file itself is never touched. At most
 * [MAX_FILES] books are remembered (oldest dropped). Best-effort: a failed write loses only the convenience.
 */
@Singleton
class QuickMarksStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, QUICK_MARKS_DIR)

    suspend fun load(checksum: String): QuickMarks? = withContext(Dispatchers.IO) {
        val file = fileFor(checksum)?.takeIf { it.isFile }
        file?.let { runCatching { decodeQuickMarks(it.readText()) }.getOrNull() }
    }

    suspend fun save(checksum: String, marks: QuickMarks) {
        withContext(Dispatchers.IO) {
            val target = fileFor(checksum)
            if (target != null) {
                runCatching {
                    dir.mkdirs()
                    val temp = File(dir, "${target.name}.part")
                    temp.writeText(encodeQuickMarks(marks))
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    prune()
                }
            }
        }
    }

    suspend fun delete(checksum: String) {
        withContext(Dispatchers.IO) { fileFor(checksum)?.let { runCatching { it.delete() } } }
    }

    private fun fileFor(checksum: String): File? = if (CHECKSUM.matches(checksum)) File(dir, "$checksum.$EXT") else null

    private fun prune() {
        dir.listFiles { file -> file.extension == EXT }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_FILES)
            .forEach { it.delete() }
    }
}

internal fun encodeQuickMarks(marks: QuickMarks): String {
    val annotations = JSONArray()
    marks.annotations.forEach { a ->
        val item = JSONObject().put("l", a.locatorJson).put("t", a.type.key)
        a.color?.let { item.put("c", it.key) }
        a.noteText?.let { item.put("n", it) }
        annotations.put(item)
    }
    val root = JSONObject().put("v", VERSION).put("bookmarks", JSONArray(marks.bookmarks))
    root.put("annotations", annotations)
    marks.locator?.let { root.put("locator", it) }
    return root.toString()
}

/** Null when the text is not a version-1 file; single bad entries are skipped, not fatal. */
internal fun decodeQuickMarks(text: String): QuickMarks? {
    val root = JSONObject(text)
    if (root.optInt("v") != VERSION) return null
    val annotations = root.optJSONArray("annotations") ?: JSONArray()
    val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray()
    val items = (0 until annotations.length()).mapNotNull { i -> annotations.optJSONObject(i)?.let(::annotationOf) }
    return QuickMarks(
        annotations = items,
        bookmarks = (0 until bookmarks.length()).map { bookmarks.getString(it) },
        locator = root.optString("locator").ifEmpty { null },
    )
}

private fun annotationOf(item: JSONObject): EpubAnnotation? {
    val type = AnnotationType.fromKey(item.optString("t"))
    val locator = item.optString("l")
    if (type == null || locator.isEmpty()) return null
    return EpubAnnotation(
        id = "",
        locatorJson = locator,
        type = type,
        color = AnnotationColor.fromKey(item.optString("c").ifEmpty { null }),
        noteText = item.optString("n").ifEmpty { null },
    )
}
