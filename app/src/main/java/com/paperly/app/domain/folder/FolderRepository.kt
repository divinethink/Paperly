package com.paperly.app.domain.folder

import kotlinx.coroutines.flow.Flow

/**
 * [type] = document type of the Library tab it belongs to; null = shown in every tab.
 * [parentId] = the folder it sits in (null = top level). A parent that no longer exists counts as top level.
 */
data class Folder(val id: String, val name: String, val type: String? = null, val parentId: String? = null)

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    /**
     * False if the name is blank, or [parentId] is missing or already at the depth limit.
     * [type] = document type of the tab it is created in.
     */
    suspend fun createFolder(name: String, type: String? = null, parentId: String? = null): Boolean

    /** False if the name is blank or the folder no longer exists. */
    suspend fun renameFolder(id: String, name: String): Boolean

    /** Documents and subfolders inside are kept: they move up to this folder's parent (top level if none). */
    suspend fun deleteFolder(id: String)

    /**
     * Folder, its subfolders and every document in them: the documents go to Trash (Restore still works, into
     * "no folder"), then the folders are removed.
     */
    suspend fun deleteFolderWithDocuments(id: String)

    /** [folderId] null = remove from folder. Ignored if the folder no longer exists. */
    suspend fun moveDocument(documentId: String, folderId: String?)

    suspend fun setTags(documentId: String, tags: List<String>)
}
