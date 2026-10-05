package com.paperly.app.domain.cover

import android.graphics.Bitmap

/** U4: cover thumbnail for a document; null => UI shows the letter cover. Never throws. */
interface CoverRepository {
    suspend fun cover(documentId: String, type: String): Bitmap?
}
