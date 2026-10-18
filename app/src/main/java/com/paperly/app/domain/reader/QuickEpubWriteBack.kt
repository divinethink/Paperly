package com.paperly.app.domain.reader

import java.io.File

/**
 * Quick-view EPUB (opened from outside, not in the Library): marks made in the Reader can be put into a copy of the
 * book or into the original file. Same safety as PDF: temp copy -> verify -> backup (7 days) -> replace; any failure
 * leaves the original untouched. Never throws into the UI.
 */
interface QuickEpubWriteBack {
    /** A separate copy of the book with [marks] written in (Save a copy / Share); null = could not make it. */
    suspend fun markedCopy(token: String, marks: List<EpubAnnotation>): File?

    /** "Replace original with marks", right now. Returns the message (string resource) to show. */
    suspend fun replaceNow(token: String, marks: List<EpubAnnotation>): Int

    /** Removes the temporary files made for [token] (call when the Reader closes). */
    fun release(token: String)
}
