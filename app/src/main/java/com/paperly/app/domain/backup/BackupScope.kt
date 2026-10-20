package com.paperly.app.domain.backup

import com.paperly.app.domain.document.LibraryType

/** What an Export puts in the ZIP: everything, or only one Library tab's documents. [fileTag] goes into the name. */
enum class BackupScope(val type: LibraryType?, val fileTag: String) {
    ALL(null, ""),
    SCAN(LibraryType.SCAN, "scanned"),
    PDF(LibraryType.PDF, "pdf"),
    EPUB(LibraryType.EPUB, "epub"),
    ;

    fun includes(docType: String): Boolean = type == null || type.docType == docType

    /** A folder with no type belongs to every tab, so it always travels along. */
    fun includesFolder(folderType: String?): Boolean = type == null || folderType == null || type.docType == folderType
}
