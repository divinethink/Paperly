package com.paperly.app.feature.scanner

import com.paperly.app.domain.folder.FolderRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The folder open on the Scanned tab: a new scan or gallery import is saved into it (null = no folder).
 * A state holder, not an event, so the Scan screen can read it whenever a save starts.
 */
@Singleton
class ScanFolderTarget @Inject constructor(private val folders: FolderRepository) {
    @Volatile
    var folderId: String? = null
        private set

    /** Called by the Scanned tab whenever its open folder changes. */
    fun select(id: String?) {
        folderId = id
    }

    /** No-op without a folder; a folder that no longer exists is ignored by the repository (scan stays in root). */
    suspend fun moveInto(documentId: String, folderId: String?) {
        if (folderId != null) folders.moveDocument(documentId, folderId)
    }
}
