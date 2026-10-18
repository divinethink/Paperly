package com.paperly.app.data.reader

import android.content.Context
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.FILE_MARK_PREFIX
import com.paperly.app.domain.reader.PdfAnnotationColors
import com.paperly.app.domain.reader.PdfAnnotationGeometry
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfAnnotationReader
import com.paperly.app.domain.reader.PdfPageBox
import com.paperly.app.domain.reader.PdfQuad
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_MARKS = 500
private const val QUAD_VALUES = 8
private const val RECT_VALUES = 4
private const val RGB_VALUES = 3
private const val HIDDEN_FLAG = 2
private const val STROKE_TOLERANCE = 0.01f

/**
 * PdfBox-Android reader, the counterpart of [PdfBoxAnnotationWriter]: same file layout, read-only. Temp-file-only
 * memory mode; a file above the writer's size limit, an encrypted or damaged file, or an OutOfMemoryError simply
 * gives no marks (the PDF itself still opens and reads normally).
 */
@Singleton
class PdfBoxAnnotationReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PdfAnnotationReader {

    override suspend fun read(file: File): List<PdfAnnotationItem> = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() > PdfBoxAnnotationWriter.MAX_BYTES) emptyList() else guarded(file)
    }

    private fun guarded(file: File): List<PdfAnnotationItem> = try {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            if (doc.isEncrypted) emptyList() else collect(doc)
        }
    } catch (ignored: OutOfMemoryError) {
        emptyList()
    } catch (ignored: Exception) {
        emptyList()
    }

    private fun collect(doc: PDDocument): List<PdfAnnotationItem> = (0 until doc.numberOfPages).asSequence()
        .flatMap { index -> pageItems(doc.getPage(index), index) }
        .take(MAX_MARKS)
        .toList()

    private fun pageItems(page: PDPage, index: Int): Sequence<PdfAnnotationItem> {
        val annots = page.getCOSObject().getDictionaryObject(COSName.ANNOTS) as? COSArray ?: return emptySequence()
        val crop = page.getCropBox()
        val box = PdfPageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.getRotation())
        if (box.width <= 0f || box.height <= 0f) return emptySequence()
        return (0 until annots.size()).asSequence().mapNotNull { j ->
            (annots.getObject(j) as? COSDictionary)?.takeIf(::isVisible)?.let { itemOf(it, index, j, box) }
        }
    }

    private fun itemOf(dict: COSDictionary, page: Int, index: Int, box: PdfPageBox): PdfAnnotationItem? {
        val type = typeOf(dict.getNameAsString(COSName.SUBTYPE)) ?: return null
        val quads = quadsOf(dict, type)
        if (quads.isEmpty()) return null
        return PdfAnnotationItem(
            id = FILE_MARK_PREFIX + (dict.getString(COSName.NM) ?: "p$page-$index"),
            page = page,
            type = type,
            color = colorOf(dict),
            rects = quads.map { PdfAnnotationGeometry.toRect(it, box) },
            noteText = dict.getString(COSName.CONTENTS)?.takeIf { it.isNotBlank() },
            stroke = strokeOf(dict, type),
        )
    }

    /** Markup: one part per `/QuadPoints` group of 8 numbers. Note: its `/Rect`. Bad data = no parts. */
    private fun quadsOf(dict: COSDictionary, type: AnnotationType): List<PdfQuad> =
        if (type == AnnotationType.NOTE) {
            listOfNotNull(rectQuad(floatsOf(dict, COSName.RECT)))
        } else {
            floatsOf(dict, COSName.QUADPOINTS).chunked(QUAD_VALUES).filter { it.size == QUAD_VALUES }
                .take(AnnotationRects.MAX_RECTS).map { part ->
                    val xs = part.filterIndexed { i, _ -> i % 2 == 0 }
                    val ys = part.filterIndexed { i, _ -> i % 2 == 1 }
                    PdfQuad(xs.min(), ys.min(), xs.max(), ys.max())
                }
        }

    private fun colorOf(dict: COSDictionary): AnnotationColor? = floatsOf(dict, COSName.C)
        .takeIf { it.size == RGB_VALUES }
        ?.let { (r, g, b) -> PdfAnnotationColors.nearest(r, g, b) }

    private fun strokeOf(dict: COSDictionary, type: AnnotationType): AnnotationStroke? {
        val width = (dict.getDictionaryObject(COSName.BS) as? COSDictionary)?.getFloat(COSName.W, -1f) ?: -1f
        return if (AnnotationStroke.appliesTo(type)) {
            AnnotationStroke.entries.firstOrNull { abs(it.dp - width) < STROKE_TOLERANCE }
        } else {
            null
        }
    }
}

private fun isVisible(dict: COSDictionary): Boolean = (dict.getInt(COSName.F, 0) and HIDDEN_FLAG) == 0

private fun typeOf(subtype: String?): AnnotationType? = when (subtype) {
    "Highlight" -> AnnotationType.HIGHLIGHT
    "Underline" -> AnnotationType.UNDERLINE
    "Squiggly" -> AnnotationType.SQUIGGLY
    "StrikeOut" -> AnnotationType.STRIKETHROUGH
    "Text" -> AnnotationType.NOTE
    else -> null
}

private fun rectQuad(values: List<Float>): PdfQuad? =
    if (values.size == RECT_VALUES) {
        val (x1, y1, x2, y2) = values
        PdfQuad(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    } else {
        null
    }

private fun floatsOf(dict: COSDictionary, key: COSName): List<Float> {
    val array = dict.getDictionaryObject(key) as? COSArray ?: return emptyList()
    return (0 until array.size()).mapNotNull { (array.getObject(it) as? COSNumber)?.floatValue() }
}
