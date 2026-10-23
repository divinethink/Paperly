package com.paperly.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.paperly.app.core.file.ExportFiles
import com.paperly.app.data.reader.PdfReaderEngine
import com.paperly.app.domain.reader.OpenResult
import com.paperly.app.domain.scanner.ExportSize
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renders every page of a PDF to its own JPG in the cache `exports/images/` folder (older files there are removed),
 * for an immediate Share / Save. One page at a time, so memory stays bounded; the source PDF is only read.
 */
@Singleton
class PdfJpgRenderer @Inject constructor(@ApplicationContext private val context: Context) {
    private val lock = Mutex() // one export at a time: each run empties the folder first

    /**
     * The page files in page order, or null when anything failed (nothing is left behind then). [only] = 0-based
     * pages to render (null = every page); file names keep the page's own number.
     */
    suspend fun render(
        source: File,
        base: String,
        only: Set<Int>? = null,
        size: ExportSize = ExportSize.ACTUAL,
    ): List<File>? = lock.withLock {
        val dir = ExportFiles.imagesDir(context)
        dir.listFiles()?.forEach { it.delete() }
        val engine = PdfReaderEngine(context)
        try {
            val pages = (engine.open(source) as? OpenResult.Success)?.pageCount ?: 0
            val wanted = (0 until pages).filter { only == null || it in only }
            val files = ArrayList<File>()
            for (index in wanted) {
                val target = File(dir, ExportFiles.imageName(base, index, pages))
                files += renderPage(engine, target, index, size) ?: break
            }
            if (wanted.isNotEmpty() && files.size == wanted.size) files else cleanUp(dir)
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

    private suspend fun renderPage(engine: PdfReaderEngine, target: File, index: Int, size: ExportSize): File? {
        val bitmap = engine.renderPage(index, size.widthPx) ?: return null
        val flat = flattenOnWhite(bitmap)
        bitmap.recycle()
        val ok = FileOutputStream(target).use { flat.compress(Bitmap.CompressFormat.JPEG, size.jpegQuality, it) }
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
}
