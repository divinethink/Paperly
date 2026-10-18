package com.paperly.app.domain.cover

import android.graphics.Bitmap

/** U4: cover thumbnail for a document; null => UI shows the letter cover. Never throws. */
interface CoverRepository {
    suspend fun cover(documentId: String, type: String): Bitmap?

    /** EPUB author line for the library card (read from the book's own metadata; cached); null if none. */
    suspend fun author(documentId: String, type: String): String?
}
