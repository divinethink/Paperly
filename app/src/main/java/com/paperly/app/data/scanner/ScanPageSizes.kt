package com.paperly.app.data.scanner

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Size of every page of a scanned PDF for the page grid: the bytes of the picture stored on that page (a scan page is
 * one JPEG). Read-only, never throws: any problem gives an empty list and the grid simply shows no sizes.
 */
@Singleton
class ScanPageSizes @Inject constructor(@ApplicationContext private val context: Context) {

    /** Page sizes in bytes, in page order; empty when the PDF cannot be read. */
    suspend fun read(file: File): List<Long> = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() > MAX_BYTES) emptyList() else guarded(file)
    }

    private fun guarded(file: File): List<Long> = try {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.isEncrypted) emptyList() else (0 until doc.numberOfPages).map { pageBytes(doc.getPage(it)) }
        }
    } catch (ignored: OutOfMemoryError) {
        emptyList()
    } catch (ignored: Exception) {
        emptyList()
    }

    private fun pageBytes(page: PDPage): Long {
        val resources = page.getResources() ?: return 0L
        var total = 0L
        for (name in resources.getXObjectNames()) {
            val image = resources.getXObject(name) as? PDImageXObject ?: continue
            total += image.getCOSObject().getInt(COSName.LENGTH, 0).toLong()
        }
        return total
    }

    private companion object {
        const val MAX_BYTES = 200L * 1024 * 1024 // a bigger file is not worth the wait; no sizes shown
    }
}
