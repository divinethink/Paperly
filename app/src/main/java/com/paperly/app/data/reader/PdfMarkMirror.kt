package com.paperly.app.data.reader

import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationStroke
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.PdfAnnotationColors
import com.paperly.app.domain.reader.PdfAnnotationItem
import com.paperly.app.domain.reader.PdfQuad
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlin.math.abs

/*
 * Helpers of PdfBoxAnnotationWriter.mirror: telling whether a mark already in the file still matches the app's
 * copy, and taking out marks that were deleted in the app. Only marks carrying our own /NM are ever touched.
 */

private const val MIRROR_SPOT = 2f // points; a moved mark differs by far more (0.01 of a page is ~6 pt)
private const val STROKE_TOLERANCE = 0.01f
private const val RGB_VALUES = 3

/** Index of the annotation named [id] (`/NM`) in [annots], or -1. */
internal fun indexOfName(annots: COSArray, id: String): Int =
    (0 until annots.size()).firstOrNull { (annots.getObject(it) as? COSDictionary)?.getString(COSName.NM) == id } ?: -1

/** True when the file's [dict] already shows [item] as it is now (type, place, colour, thickness, note text). */
internal fun markMatches(dict: COSDictionary, item: PdfAnnotationItem, bounds: PdfQuad): Boolean {
    val wantedText = item.noteText?.takeIf { it.isNotBlank() }.orEmpty()
    val isNote = item.type == AnnotationType.NOTE
    return dict.getNameAsString(COSName.SUBTYPE) == fileSubtype(item.type) &&
        dict.getString(COSName.CONTENTS).orEmpty() == wantedText &&
        (isNote || sameSpot(dict, bounds, MIRROR_SPOT)) &&
        sameColor(dict, item) &&
        sameStroke(dict, item)
}

private fun sameColor(dict: COSDictionary, item: PdfAnnotationItem): Boolean {
    val c = numbersOf(dict, COSName.C)
    val found = if (c.size == RGB_VALUES) PdfAnnotationColors.nearest(c[0], c[1], c[2]) else null
    return found == (item.color ?: AnnotationColor.defaultFor(item.type))
}

private fun sameStroke(dict: COSDictionary, item: PdfAnnotationItem): Boolean {
    val wanted = item.stroke?.takeIf { AnnotationStroke.appliesTo(item.type) } ?: return true
    val width = (dict.getDictionaryObject(COSName.BS) as? COSDictionary)?.getFloat(COSName.W, -1f) ?: -1f
    return abs(width - wanted.dp) < STROKE_TOLERANCE
}

private fun numbersOf(dict: COSDictionary, key: COSName): List<Float> {
    val array = dict.getDictionaryObject(key) as? COSArray ?: return emptyList()
    return (0 until array.size()).mapNotNull { (array.getObject(it) as? COSNumber)?.floatValue() }
}

/** Removes the marks named in [removed] from every page; returns the names really taken out. */
internal fun removeNamedMarks(doc: PDDocument, removed: Set<String>): List<String> {
    val gone = ArrayList<String>()
    if (removed.isEmpty()) return gone
    for (page in 0 until doc.numberOfPages) {
        val annots = doc.getPage(page).getCOSObject().getDictionaryObject(COSName.ANNOTS) as? COSArray ?: continue
        for (i in annots.size() - 1 downTo 0) {
            val name = (annots.getObject(i) as? COSDictionary)?.getString(COSName.NM)
            if (name != null && name in removed) {
                annots.remove(i)
                gone.add(name)
            }
        }
    }
    return gone
}
