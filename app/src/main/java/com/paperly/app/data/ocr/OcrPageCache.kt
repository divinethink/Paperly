package com.paperly.app.data.ocr

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CACHE_DIR = "ocr1" // bump when the recognition setup changes: old text is simply re-read

/** Per-page OCR text in the OS-clearable cache dir; a missing entry only means the page is read again. */
class OcrPageCache(private val context: Context) {
    private fun file(documentId: String, index: Int) = File(context.cacheDir, "$CACHE_DIR/$documentId/p$index.txt")

    suspend fun read(documentId: String, index: Int): String? = withContext(Dispatchers.IO) {
        try {
            file(documentId, index).takeIf { it.isFile }?.readText()
        } catch (e: IOException) {
            null
        }
    }

    suspend fun write(documentId: String, index: Int, text: String) = withContext(Dispatchers.IO) {
        try {
            val target = file(documentId, index)
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            part.writeText(text)
            if (!part.renameTo(target)) part.delete()
            Unit
        } catch (e: IOException) {
            Unit
        }
    }
}
