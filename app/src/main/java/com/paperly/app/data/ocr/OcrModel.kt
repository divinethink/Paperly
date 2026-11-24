package com.paperly.app.data.ocr

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val MIN_BYTES = 100_000L
private const val TIMEOUT_MS = 20_000

/** Bengali + English `tessdata_fast` models (Apache-2.0), downloaded once into app-private storage; not in the APK. */
object OcrModel {
    private val languages = listOf("ben", "eng")

    /** Tesseract language spec for the loaded models. */
    const val LANG_SPEC = "ben+eng"

    fun dataDir(context: Context) = File(context.filesDir, "ocr")

    private fun modelFile(context: Context, lang: String) =
        File(File(dataDir(context), "tessdata"), "$lang.traineddata")

    fun isReady(context: Context) = languages.all { modelFile(context, it).length() >= MIN_BYTES }

    /** Blocking (call off the main thread). Fetches the missing models; true when all are usable. */
    fun download(context: Context): Boolean =
        languages.all { modelFile(context, it).length() >= MIN_BYTES || fetch(modelFile(context, it), it) }

    /** Writes `.part`, checks the pinned size and SHA-256, then renames: a wrong or broken download is never kept. */
    private fun fetch(target: File, lang: String): Boolean {
        val part = File(target.parentFile, target.name + ".part")
        val ok = try {
            target.parentFile?.mkdirs()
            copyTo(part, OcrModelPin.url(lang)) && OcrModelPin.matches(lang, part) && part.renameTo(target)
        } catch (e: IOException) {
            false
        }
        if (!ok) part.delete()
        return ok
    }

    private fun copyTo(part: File, url: String): Boolean {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.responseCode == HttpURLConnection.HTTP_OK && save(conn, part)
        } finally {
            conn.disconnect()
        }
    }

    private fun save(conn: HttpURLConnection, part: File): Boolean {
        conn.inputStream.use { Files.copy(it, part.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return true
    }
}
