package com.paperly.app.data.scanner

import android.content.Context
import android.graphics.Bitmap
import com.paperly.app.domain.scanner.ScanEditPage
import com.paperly.app.domain.scanner.ScanOutputSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a PDF with one JPEG per page (PdfBox, DCT-embedded: small files; android.graphics.pdf would store raw
 * pixels). One page bitmap alive at a time; PdfBox runs in temp-file mode.
 */
@Singleton
class ScanPdfBuilder @Inject constructor(@ApplicationContext private val context: Context) {

    /** [source] gives the image file of an editor page. Throws on any failure; the caller owns [out]'s cleanup. */
    fun build(out: File, pages: List<ScanEditPage>, size: ScanOutputSize, source: (ScanEditPage) -> File) {
        PDFBoxResourceLoader.init(context)
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            pages.forEach { page ->
                val bitmap = ScanPageRenderer.renderFile(
                    source(page),
                    page.quad,
                    page.rotation,
                    page.filter,
                    size.maxSide,
                )
                try {
                    addPage(doc, bitmap, size.jpegQuality)
                } finally {
                    bitmap.recycle()
                }
            }
            doc.save(out)
        }
    }

    private fun addPage(doc: PDDocument, bitmap: Bitmap, quality: Int) {
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val scale = PAGE_LONG_SIDE_PT / maxOf(bitmap.width, bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val page = PDPage(PDRectangle(width, height))
        doc.addPage(page)
        val image = JPEGFactory.createFromByteArray(doc, jpeg.toByteArray())
        PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, width, height) }
    }

    private companion object {
        const val PAGE_LONG_SIDE_PT = 842f // A4 long side in points; the image keeps its own aspect ratio
    }
}
