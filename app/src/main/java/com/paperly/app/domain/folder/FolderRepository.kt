package com.paperly.app.domain.folder

import kotlinx.coroutines.flow.Flow

data class Folder(val id: String, val name: String)

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    /** False if the name is blank. */
    suspend fun createFolder(name: String): Boolean

    /** Documents inside are kept and become "no folder". */
    suspend fun deleteFolder(id: String)

    /** [folderId] null = remove from folder. Ignored if the folder no longer exists. */
    suspend fun moveDocument(documentId: String, folderId: String?)

    suspend fun setTags(documentId: String, tags: List<String>)
}
