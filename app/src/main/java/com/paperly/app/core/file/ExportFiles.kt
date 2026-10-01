package com.paperly.app.core.file

import android.content.Context
import java.io.File
import java.io.IOException

/** Export helpers: only the cache `exports/` subfolder is ever exposed through FileProvider. */
object ExportFiles {
    private const val DIR = "exports"
    private const val MAX_NAME = 80
    private val unsafeChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    fun isPdf(type: String): Boolean = type != "epub"

    fun mimeType(type: String): String = if (isPdf(type)) "application/pdf" else "application/epub+zip"

    fun fileName(title: String, type: String): String {
        val base = title.replace(unsafeChars, "_").trim().take(MAX_NAME).ifBlank { "document" }
        return base + if (isPdf(type)) ".pdf" else ".epub"
    }

    /** Copies [source] to a fresh export file (older exports removed); the original is never touched. */
    fun copyToExports(context: Context, source: File, name: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, name)
        source.copyTo(target, overwrite = true)
        if (target.length() != source.length()) {
            target.delete()
            throw IOException("Export copy size mismatch")
        }
        return target
    }
}
