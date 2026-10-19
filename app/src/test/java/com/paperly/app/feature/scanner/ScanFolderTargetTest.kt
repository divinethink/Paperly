package com.paperly.app.feature.scanner

import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanFolderTargetTest {
    private val moves = mutableListOf<Pair<String, String?>>()

    private val folders = object : FolderRepository {
        override fun observeFolders(): Flow<List<Folder>> = emptyFlow()
        override suspend fun createFolder(name: String, type: String?, parentId: String?) = true
        override suspend fun renameFolder(id: String, name: String) = true
        override suspend fun deleteFolder(id: String) = Unit
        override suspend fun deleteFolderWithDocuments(id: String) = Unit
        override suspend fun moveDocument(documentId: String, folderId: String?) {
            moves += documentId to folderId
        }
        override suspend fun setTags(documentId: String, tags: List<String>) = Unit
    }

    @Test fun startsWithoutAFolderAndFollowsTheSelection() {
        val target = ScanFolderTarget(folders)
        assertNull(target.folderId)
        target.select("f1")
        assertEquals("f1", target.folderId)
        target.select(null)
        assertNull(target.folderId)
    }

    @Test fun aScanIsMovedIntoTheOpenFolder() = runBlocking {
        ScanFolderTarget(folders).moveInto("d1", "f1")
        assertEquals(listOf("d1" to "f1"), moves)
    }

    @Test fun withoutAFolderTheScanStaysInTheRoot() = runBlocking {
        ScanFolderTarget(folders).moveInto("d1", null)
        assertEquals(emptyList<Pair<String, String?>>(), moves)
    }
}
