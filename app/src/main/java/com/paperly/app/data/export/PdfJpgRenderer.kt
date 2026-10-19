package com.paperly.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.domain.reader.OpenResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders every page of a PDF to its own JPG in the cache `exports/images/` folder (older files there are removed),
 * for an immediate Share / Save. One page at a time, so memory stays bounded; the source PDF is only read.
 */
@Singleton
class PdfJpgRenderer @Inject constructor(@ApplicationContext private val context: Context) {

    /** The page files in page order, or null when anything failed (nothing is left behind then). */
    suspend fun render(source: File, base: String): List<File>? {
        val dir = ExportFiles.imagesDir(context)
        dir.listFiles()?.forEach { it.delete() }
        val engine = PdfReaderEngine(context)
        return try {
            val pages = (engine.open(source) as? OpenResult.Success)?.pageCount ?: 0
            val files = ArrayList<File>()
            for (index in 0 until pages) {
                files += renderPage(engine, File(dir, ExportFiles.imageName(base, index, pages)), index) ?: break
            }
            if (pages > 0 && files.size == pages) files else cleanUp(dir)
        } catch (ignored: IOException) {
            cleanUp(dir)
        } catch (ignored: OutOfMemoryError) {
            cleanUp(dir)
        } finally {
            engine.close()
        }
    }

    private fun cleanUp(dir: File): List<File>? {
        dir.listFiles()?.forEach { it.delete() }
        return null
    }

    private suspend fun renderPage(engine: PdfReaderEngine, target: File, index: Int): File? {
        val bitmap = engine.renderPage(index, MAX_WIDTH) ?: return null
        val flat = flattenOnWhite(bitmap)
        bitmap.recycle()
        val ok = FileOutputStream(target).use { flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        flat.recycle()
        return target.takeIf { ok && it.length() > 0 }
    }

    /** JPEG has no transparency: draw on white so empty areas are not black. */
    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return result
    }

    private companion object {
        const val MAX_WIDTH = 1600
        const val JPEG_QUALITY = 90
    }
}
