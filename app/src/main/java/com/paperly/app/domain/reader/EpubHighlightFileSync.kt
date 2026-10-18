package com.paperly.app.domain.reader

/** Asks for the marks of a Library EPUB to be written into the book file (if the setting is on). */
interface EpubHighlightFileSync {
    /** Fire and forget (app scope, survives the Reader closing). The Reader must have closed the book first. */
    fun schedule(documentId: String)
}
