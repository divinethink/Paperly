package com.paperly.app.feature.reader

import android.graphics.Bitmap

/**
 * Hand-over of the tapped page thumbnail from the scan page grid to the Reader, so the Reader shows that page at once
 * instead of a black screen while the PDF opens. One slot, keyed by document; read once, then cleared.
 */
object ScanOpenPreview {
    private var documentId: String? = null
    private var bitmap: Bitmap? = null

    @Synchronized
    fun put(id: String, thumb: Bitmap) {
        documentId = id
        bitmap = thumb
    }

    @Synchronized
    fun take(id: String): Bitmap? {
        val found = if (documentId == id) bitmap else null
        documentId = null
        bitmap = null
        return found
    }
}
