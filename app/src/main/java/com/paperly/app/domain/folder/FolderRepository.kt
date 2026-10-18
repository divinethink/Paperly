package com.paperly.app.domain.folder

import kotlinx.coroutines.flow.Flow

/** [type] = document type of the Library tab it belongs to; null = shown in every tab. */
data class Folder(val id: String, val name: String, val type: String? = null)

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    /** False if the name is blank. [type] = document type of the tab it is created in. */
    suspend fun createFolder(name: String, type: String? = null): Boolean

    /** Documents inside are kept and become "no folder". */
    suspend fun deleteFolder(id: String)

    /** Folder and everything in it: the documents go to Trash (Restore still works), then the folder is removed. */
    suspend fun deleteFolderWithDocuments(id: String)

    /** [folderId] null = remove from folder. Ignored if the folder no longer exists. */
    suspend fun moveDocument(documentId: String, folderId: String?)

    suspend fun setTags(documentId: String, tags: List<String>)
}
