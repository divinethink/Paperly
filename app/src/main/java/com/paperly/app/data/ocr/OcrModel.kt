package com.paperly.app.data.ocr

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val MODEL_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/ben.traineddata"
private const val MIN_BYTES = 100_000L
private const val TIMEOUT_MS = 20_000
private const val KB = 1024L

/** Bengali `tessdata_fast` model (Apache-2.0), downloaded once into app-private storage; never bundled in the APK. */
object OcrModel {
    fun dataDir(context: Context) = File(context.filesDir, "ocr")

    private fun modelFile(context: Context) = File(File(dataDir(context), "tessdata"), "ben.traineddata")

    fun isReady(context: Context) = modelFile(context).length() >= MIN_BYTES

    fun sizeKb(context: Context) = modelFile(context).length() / KB

    /** Blocking (call off the main thread). Writes `.part`, checks the size, then renames; true when usable. */
    fun download(context: Context): Boolean {
        val target = modelFile(context)
        val part = File(target.parentFile, target.name + ".part")
        return try {
            target.parentFile?.mkdirs()
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                }
            } finally {
                conn.disconnect()
            }
            val ok = part.length() >= MIN_BYTES && part.renameTo(target)
            if (!ok) part.delete()
            ok
        } catch (e: IOException) {
            part.delete()
            false
        }
    }
}
