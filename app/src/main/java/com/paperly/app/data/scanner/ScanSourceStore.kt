package com.paperly.app.data.scanner

import android.content.Context
import android.net.Uri
import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The kept page images: `files/scans/<documentId>/p<i>.img`. Plain file work, no database. */
@Singleton
class ScanSourceStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun dir(documentId: String): File? =
        if (SAFE_DOCUMENT_ID.matches(documentId)) File(context.filesDir, "$SCANS_DIR/$documentId") else null

    fun file(documentId: String, index: Int): File? =
        dir(documentId)?.let { File(it, pageName(index)) }?.takeIf { it.isFile }

    /** Contiguous `p0..pN-1` files; anything else (gap, stray file, none) = 0 = not editable. */
    fun count(documentId: String): Int {
        val dir = dir(documentId)?.takeIf { it.isDirectory } ?: return 0
        var n = 0
        while (File(dir, pageName(n)).isFile) n++
        return if (dir.list().orEmpty().size == n) n else 0
    }

    /** All or nothing (a half set would shift pages). Own temp files (file scheme) are deleted after success. */
    fun keep(documentId: String, pageUris: List<String>): Boolean {
        val dir = dir(documentId) ?: return false
        return try {
            dir.mkdirs()
            pageUris.forEachIndexed { i, uri -> copy(Uri.parse(uri), File(dir, pageName(i))) }
            pageUris.forEach { deleteIfOwn(Uri.parse(it)) }
            true
        } catch (e: IOException) {
            dir.deleteRecursively()
            false
        } catch (e: SecurityException) {
            dir.deleteRecursively()
            false
        }
    }

    /** Gallery picks -> a new cache folder; returns it, or null (nothing left behind) on failure. */
    fun stage(uris: List<String>, id: String): File? {
        val dir = File(File(context.cacheDir, GALLERY_DIR), id)
        return try {
            dir.mkdirs()
            uris.forEachIndexed { i, uri -> copy(Uri.parse(uri), File(dir, pageName(i))) }
            dir
        } catch (e: IOException) {
            dir.deleteRecursively()
            null
        } catch (e: SecurityException) {
            dir.deleteRecursively()
            null
        }
    }

    fun galleryPdf(id: String): File = File(File(context.cacheDir, GALLERY_DIR).apply { mkdirs() }, "$id.pdf")

    fun pageName(index: Int) = "p$index.img"

    private fun copy(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open page image")
        input.use { source -> target.outputStream().buffered().use { source.copyTo(it) } }
    }

    private fun deleteIfOwn(uri: Uri) {
        if (uri.scheme == "file") runCatching { uri.path?.let { File(it).delete() } }
    }

    private companion object {
        const val SCANS_DIR = "scans"
        const val GALLERY_DIR = "scan_gallery"
    }
}
