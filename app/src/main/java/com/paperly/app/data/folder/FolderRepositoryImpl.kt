package com.paperly.app.data.folder

import com.paperly.app.core.database.Converters
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.FolderDao
import com.paperly.app.core.database.FolderEntity
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.normalizeFolderName
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val documentDao: DocumentDao,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders().map { list -> list.map { Folder(it.folderId, it.name) } }

    override suspend fun createFolder(name: String): Boolean {
        val clean = normalizeFolderName(name) ?: return false
        val entity = FolderEntity(UUID.randomUUID().toString(), clean, null, System.currentTimeMillis())
        return folderDao.insert(entity) != -1L
    }

    override suspend fun deleteFolder(id: String) {
        folderDao.deleteAndUnassign(id)
    }

    override suspend fun moveDocument(documentId: String, folderId: String?) {
        if (folderId != null && folderDao.countById(folderId) == 0) return // folder deleted meanwhile
        documentDao.updateFolder(documentId, folderId)
    }

    override suspend fun setTags(documentId: String, tags: List<String>) {
        documentDao.updateTags(documentId, Converters().fromTags(tags.takeIf { it.isNotEmpty() }))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FolderDataModule {
    @Binds
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
