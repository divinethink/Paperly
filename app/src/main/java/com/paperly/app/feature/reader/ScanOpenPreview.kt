package com.paperly.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

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

/** While the document opens: the tapped scan page's thumbnail if there is one, else the spinner. */
@Composable
internal fun OpeningPlaceholder(preview: Bitmap?) {
    if (preview == null) {
        CircularProgressIndicator()
    } else {
        Image(
            preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
