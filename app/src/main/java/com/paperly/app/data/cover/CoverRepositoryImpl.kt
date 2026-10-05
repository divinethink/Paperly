package com.paperly.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.domain.cover.CoverRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * U4: EPUB declared cover / PDF first page -> small JPEG cache (cacheDir/covers) + in-memory LRU.
 * Cache key = id + source mtime, so a changed file is re-rendered. Any failure (encrypted PDF, bad EPUB,
 * OOM) => null => letter cover. Source files are only read, never modified.
 */
@Singleton
class CoverRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: DocumentFileStore,
) : CoverRepository {
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(2) // bound concurrent decodes while scrolling
    private val dir by lazy { File(context.cacheDir, "covers").apply { mkdirs() } }

    override suspend fun cover(documentId: String, type: String): Bitmap? = withContext(Dispatchers.IO) {
        val src = files.resolve(documentId)?.takeIf { it.isFile } ?: return@withContext null
        val key = "$documentId-${src.lastModified()}"
        memory.get(key)?.let { return@withContext it }
        val disk = File(dir, "$key.jpg")
        val cached = if (disk.isFile) BitmapFactory.decodeFile(disk.path) else null
        val bmp = cached ?: gate.withPermit { render(src, type) }?.also { save(it, disk, documentId) }
        bmp?.also { memory.put(key, it) }
    }

    private fun render(src: File, type: String): Bitmap? = try {
        if (type == "epub") epubCover(src) else pdfFirstPage(src)
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun pdfFirstPage(src: File): Bitmap? =
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val r = PdfRenderer(fd) // SecurityException (password PDF) -> caught in render() -> letter cover
            try {
                if (r.pageCount == 0) return null
                val page = r.openPage(0)
                try {
                    val h = (WIDTH_PX * page.height.toFloat() / page.width).toInt().coerceIn(1, MAX_HEIGHT_PX)
                    val b = Bitmap.createBitmap(WIDTH_PX, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                } finally {
                    page.close()
                }
            } finally {
                r.close()
            }
        }

    private fun epubCover(src: File): Bitmap? = ZipFile(src).use { zip ->
        val entry = EpubCoverParser.coverPath(zip)?.let { zip.getEntry(it) } ?: return null
        if (entry.size > MAX_IMAGE_BYTES) return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= WIDTH_PX) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun save(b: Bitmap, f: File, id: String) {
        try {
            dir.listFiles { x -> x.name.startsWith("$id-") && x != f }?.forEach { it.delete() } // drop stale versions
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Exception) {
            f.delete()
        }
    }

    private companion object {
        const val WIDTH_PX = 360
        const val MAX_HEIGHT_PX = 720
        const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
        const val MEMORY_BYTES = 16 * 1024 * 1024
    }
}
