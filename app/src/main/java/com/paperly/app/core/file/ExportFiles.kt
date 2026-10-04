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

    fun baseName(title: String): String = title.replace(unsafeChars, "_").trim().take(MAX_NAME).ifBlank { "document" }

    fun fileName(title: String, type: String): String = baseName(title) + if (isPdf(type)) ".pdf" else ".epub"

    /** Image-export output folder (inside the FileProvider-exposed `exports/`; not wiped by document shares). */
    fun imagesDir(context: Context): File = File(context.cacheDir, "$DIR/images").apply { mkdirs() }

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

    /** Removes files (and then-empty sub-folders) under [dir] not modified since [cutoffMillis]. Returns files removed. */
    fun pruneOlderThan(dir: File, cutoffMillis: Long): Int {
        var removed = 0
        dir.listFiles().orEmpty().forEach { f ->
            if (f.isDirectory) {
                removed += pruneOlderThan(f, cutoffMillis)
                f.delete() // only succeeds when it is now empty
            } else if (f.lastModified() < cutoffMillis && f.delete()) {
                removed++
            }
        }
        return removed
    }

    /** The FileProvider-exposed cache folder (see [copyToExports]). */
    fun exportsDir(context: Context): File = File(context.cacheDir, DIR)
}
