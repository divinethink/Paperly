package com.paperly.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentConstantsTest {
    @Test
    fun knownDocumentTypesAreValid() {
        assertTrue(DocumentType.isValid(DocumentType.PDF))
        assertTrue(DocumentType.isValid(DocumentType.EPUB))
        assertTrue(DocumentType.isValid(DocumentType.SCANNED_PDF))
    }

    @Test
    fun unknownDocumentTypeIsRejected() {
        assertFalse(DocumentType.isValid("mobi"))
        assertFalse(DocumentType.isValid(""))
    }

    @Test
    fun storageStatesAreValidated() {
        assertTrue(StorageState.isValid(StorageState.LOCAL_ONLY))
        assertTrue(StorageState.isValid(StorageState.CONFLICT))
        assertFalse(StorageState.isValid("DELETED"))
    }
}
