package com.paperly.app.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.paperly.app.core.file.SAFE_DOCUMENT_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Parent of every document's kept page images (also cleared by "Delete all my data"). */
internal const val SCANS_DIR = "scans"

/** The kept page images: `files/scans/<documentId>/p<i>.img`. Plain file work, no database. */
@Singleton
@Suppress("TooManyFunctions") // plain file helpers, one per folder operation
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
            uris.forEachIndexed { i, uri -> copyShrunk(Uri.parse(uri), File(dir, pageName(i))) }
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

    private fun stagedDir(documentId: String): File? =
        if (SAFE_DOCUMENT_ID.matches(documentId)) File(File(context.cacheDir, ADD_DIR), documentId) else null

    /** S4: pictures to add, copied aside (not yet part of the scan). Returns their file names; none left on failure. */
    fun stageAdded(documentId: String, uris: List<String>): List<String>? {
        val dir = stagedDir(documentId) ?: return null
        return try {
            dir.mkdirs()
            uris.map { uri ->
                val name = "a${UUID.randomUUID()}.img"
                copyShrunk(Uri.parse(uri), File(dir, name))
                name
            }
        } catch (e: IOException) {
            null.also { dir.deleteRecursively() }
        } catch (e: SecurityException) {
            null.also { dir.deleteRecursively() }
        }
    }

    fun stagedFile(documentId: String, name: String): File? =
        if (STAGED_NAME.matches(name)) stagedDir(documentId)?.let { File(it, name) }?.takeIf { it.isFile } else null

    fun discardStaged(documentId: String) {
        stagedDir(documentId)?.deleteRecursively()
    }

    private fun nextDir(documentId: String): File? =
        if (SAFE_DOCUMENT_ID.matches(documentId)) File(context.filesDir, "$SCANS_DIR/$documentId.next") else null

    /**
     * S4: the page images in their NEW order as `p0..` in a side folder (`<id>.next`); the live folder is untouched
     * until [commitNext]. Throws on failure (the caller then calls [discardNext]).
     */
    fun prepareNext(documentId: String, sources: List<File>) {
        val next = nextDir(documentId) ?: throw IOException("Bad document id")
        next.deleteRecursively()
        next.mkdirs()
        sources.forEachIndexed { i, file -> file.copyTo(File(next, pageName(i)), overwrite = true) }
    }

    /** Replaces the live folder with the prepared one (call only after the file and rows changed). */
    fun commitNext(documentId: String) {
        val live = dir(documentId) ?: return
        val next = nextDir(documentId)?.takeIf { it.isDirectory } ?: return
        live.deleteRecursively()
        if (!next.renameTo(live)) {
            next.copyRecursively(live, overwrite = true)
            next.deleteRecursively()
        }
    }

    fun discardNext(documentId: String) {
        nextDir(documentId)?.deleteRecursively()
    }

    /** Permanent delete of a document: its page images, any half-done page edit and staged pictures. */
    fun deleteAll(documentId: String) {
        dir(documentId)?.deleteRecursively()
        nextDir(documentId)?.deleteRecursively()
        stagedDir(documentId)?.deleteRecursively()
    }

    private fun copy(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open page image")
        input.use { source -> target.outputStream().buffered().use { source.copyTo(it) } }
    }

    /**
     * Gallery pictures are phone photos (several MB each). A copy longer than [KEEP_SIDE] px is scaled down and
     * re-saved as JPEG (still above the largest output size, so no visible loss); anything else is copied as it is.
     */
    private fun copyShrunk(uri: Uri, target: File) {
        val raw = File(target.parentFile, "${target.name}.raw")
        try {
            copy(uri, raw)
            if (!shrink(raw, target)) raw.copyTo(target, overwrite = true)
        } finally {
            raw.delete()
        }
    }

    /** false = not shrunk (already small, unreadable, or out of memory): the caller keeps the original bytes. */
    private fun shrink(raw: File, target: File): Boolean = try {
        val bitmap = ScanPageRenderer.decode(raw, KEEP_SIDE)
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= KEEP_SIDE) {
            bitmap.recycle()
            false
        } else {
            val ratio = KEEP_SIDE.toFloat() / longSide
            val small = Bitmap.createScaledBitmap(
                bitmap,
                maxOf(1, (bitmap.width * ratio).toInt()),
                maxOf(1, (bitmap.height * ratio).toInt()),
                true,
            )
            if (small !== bitmap) bitmap.recycle()
            try {
                target.outputStream().buffered().use { small.compress(Bitmap.CompressFormat.JPEG, KEEP_QUALITY, it) }
            } finally {
                small.recycle()
            }
        }
    } catch (ignored: IOException) {
        false
    } catch (ignored: OutOfMemoryError) {
        false
    }

    private fun deleteIfOwn(uri: Uri) {
        if (uri.scheme == "file") runCatching { uri.path?.let { File(it).delete() } }
    }

    private companion object {
        const val GALLERY_DIR = "scan_gallery"
        const val ADD_DIR = "scan_add"
        const val KEEP_SIDE = 2480 // = the largest output size's long side
        const val KEEP_QUALITY = 90
        val STAGED_NAME = Regex("a[0-9a-f-]{36}\\.img")
    }
}
