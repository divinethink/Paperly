package com.paperly.app.data.export

import android.content.Context
import com.paperly.app.core.file.ExportFiles
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A copy of a PDF with only some pages, for printing the pages chosen in the grid. The source file is only read;
 * the copy goes to the cache `exports/print/` folder (files older than a day are removed). Never throws: null = failed.
 */
@Singleton
class PdfPageSubset @Inject constructor(@ApplicationContext private val context: Context) {

    /** [pages] = 0-based pages to keep; they stay in document order. */
    suspend fun extract(source: File, pages: Set<Int>, base: String): File? = withContext(Dispatchers.IO) {
        if (pages.isEmpty() || !source.isFile) return@withContext null
        val dir = File(ExportFiles.exportsDir(context), "print").apply { mkdirs() }
        ExportFiles.pruneOlderThan(dir, System.currentTimeMillis() - DAY_MS)
        val target = File(dir, "$base-${System.currentTimeMillis()}.pdf")
        if (copyPages(source, pages, target)) target else null.also { target.delete() }
    }

    private fun copyPages(source: File, pages: Set<Int>, target: File): Boolean = try {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc -> saveOnly(doc, pages, target) }
    } catch (ignored: OutOfMemoryError) {
        false
    } catch (ignored: Exception) {
        false
    }

    /** Removes every page not in [pages] and saves the rest to [target]; false if nothing would be left. */
    private fun saveOnly(doc: PDDocument, pages: Set<Int>, target: File): Boolean {
        val keep = pages.filter { it in 0 until doc.numberOfPages }.toSet()
        if (doc.isEncrypted || keep.isEmpty()) return false
        (doc.numberOfPages - 1 downTo 0).filter { it !in keep }.forEach { doc.removePage(it) }
        doc.save(target)
        return target.length() > 0
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
