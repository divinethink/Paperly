package com.paperly.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.domain.reader.OpenResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Renders every PDF page to JPG/PNG in the background (P5-D). One page -> the image itself, otherwise a ZIP.
 * Output is written to `<name>.part`, validated, then renamed; the source PDF is only read.
 */
class ImageExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = File(inputData.getString(KEY_SOURCE).orEmpty())
        val base = inputData.getString(KEY_NAME).orEmpty().ifBlank { "document" }
        val png = inputData.getString(KEY_FORMAT) == FORMAT_PNG
        val dir = ExportFiles.imagesDir(applicationContext)
        dir.listFiles()?.forEach { it.delete() }
        val engine = PdfReaderEngine(applicationContext)
        return try {
            val pages = (engine.open(source) as? OpenResult.Success)?.pageCount ?: 0
            val file = if (pages > 0) export(engine, pages, dir, base, png) else null
            if (file != null && validate(file, pages)) {
                val mime = if (pages > 1) "application/zip" else if (png) "image/png" else "image/jpeg"
                Result.success(workDataOf(KEY_PATH to file.absolutePath, KEY_MIME to mime))
            } else {
                dir.listFiles()?.forEach { it.delete() }
                Result.failure()
            }
        } catch (e: CancellationException) {
            dir.listFiles()?.forEach { it.delete() }
            throw e
        } catch (e: IOException) {
            dir.listFiles()?.forEach { it.delete() }
            Result.failure()
        } finally {
            engine.close()
        }
    }

    private suspend fun export(engine: PdfReaderEngine, pages: Int, dir: File, base: String, png: Boolean): File? {
        val ext = if (png) "png" else "jpg"
        val target = File(dir, if (pages == 1) "$base.$ext" else "$base-images.zip")
        val part = File(dir, target.name + ".part")
        val written = if (pages == 1) writeSingle(engine, part, png) else writeZip(engine, pages, part, png, ext)
        return if (written && part.renameTo(target)) target else null
    }

    private suspend fun writeSingle(engine: PdfReaderEngine, part: File, png: Boolean): Boolean {
        val data = renderEncoded(engine, 0, png) ?: return false
        part.writeBytes(data)
        setProgress(workDataOf(KEY_DONE to 1, KEY_TOTAL to 1))
        return true
    }

    private suspend fun writeZip(engine: PdfReaderEngine, pages: Int, part: File, png: Boolean, ext: String): Boolean {
        ZipOutputStream(FileOutputStream(part).buffered()).use { zip ->
            for (i in 0 until pages) {
                val data = renderEncoded(engine, i, png) ?: return false
                zip.putNextEntry(ZipEntry("page-" + (i + 1).toString().padStart(PAD, '0') + "." + ext))
                zip.write(data)
                zip.closeEntry()
                setProgress(workDataOf(KEY_DONE to i + 1, KEY_TOTAL to pages))
            }
        }
        return true
    }

    /** One page at a time so memory stays bounded; JPEG is flattened onto white (no black transparent areas). */
    private suspend fun renderEncoded(engine: PdfReaderEngine, index: Int, png: Boolean): ByteArray? {
        val bitmap = engine.renderPage(index, MAX_WIDTH) ?: return null
        val flat = if (png) bitmap else flattenOnWhite(bitmap)
        val out = ByteArrayOutputStream()
        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val ok = flat.compress(format, JPEG_QUALITY, out)
        if (flat !== bitmap) flat.recycle()
        bitmap.recycle()
        return if (ok) out.toByteArray() else null
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return result
    }

    private fun validate(file: File, pages: Int): Boolean = file.length() > 0 &&
        if (pages == 1) decodesAsImage(file) else zipHasPages(file, pages)

    private fun decodesAsImage(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun zipHasPages(file: File, pages: Int): Boolean = try {
        ZipFile(file).use { it.size() == pages }
    } catch (e: IOException) {
        false
    }

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_NAME = "name"
        const val KEY_FORMAT = "format"
        const val KEY_PATH = "path"
        const val KEY_MIME = "mime"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val FORMAT_PNG = "png"
        const val FORMAT_JPG = "jpg"
        private const val MAX_WIDTH = 1600
        private const val JPEG_QUALITY = 90
        private const val PAD = 3
    }
}
