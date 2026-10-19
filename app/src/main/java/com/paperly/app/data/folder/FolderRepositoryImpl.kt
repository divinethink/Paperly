package com.paperly.app.data.folder

import com.paperly.app.core.database.Converters
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.FolderDao
import com.paperly.app.core.database.FolderEntity
import com.paperly.app.core.database.TrashDao
import com.paperly.app.domain.folder.Folder
import com.paperly.app.domain.folder.FolderRepository
import com.paperly.app.domain.folder.canNestUnder
import com.paperly.app.domain.folder.normalizeFolderName
import com.paperly.app.domain.folder.parentOf
import com.paperly.app.domain.folder.subtreeIds
import com.paperly.app.domain.sync.SyncQueue
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
    private val trashDao: TrashDao,
    private val syncQueue: SyncQueue,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders().map { list -> list.map { it.toDomain() } }

    private suspend fun currentFolders(): List<Folder> = folderDao.getAll().map { it.toDomain() }

    override suspend fun createFolder(name: String, type: String?, parentId: String?): Boolean {
        val clean = normalizeFolderName(name) ?: return false
        if (!currentFolders().canNestUnder(parentId)) return false // parent gone, or already at the depth limit
        val entity = FolderEntity(UUID.randomUUID().toString(), clean, parentId, System.currentTimeMillis(), type)
        return folderDao.insert(entity) != -1L
    }

    override suspend fun renameFolder(id: String, name: String): Boolean {
        val clean = normalizeFolderName(name) ?: return false
        return folderDao.rename(id, clean) > 0
    }

    override suspend fun deleteFolder(id: String) {
        val all = currentFolders()
        val lift = all.firstOrNull { it.id == id }?.let { all.parentOf(it)?.id } // contents move up one level
        val affected = documentDao.getIdsByFolder(id) // read first: afterwards they are no longer in the folder
        folderDao.deleteAndLift(id, lift)
        syncQueue.documentsChanged(affected)
    }

    /** Each step is repeatable (softDelete only touches live rows), so a retry after a crash just finishes the job. */
    override suspend fun deleteFolderWithDocuments(id: String) {
        val ids = currentFolders().subtreeIds(id)
        val affected = ids.flatMap { documentDao.getIdsByFolder(it) }
        val now = System.currentTimeMillis()
        affected.forEach { trashDao.softDelete(it, now) }
        folderDao.deleteSubtree(ids.toList())
        syncQueue.documentsChanged(affected)
    }

    override suspend fun moveDocument(documentId: String, folderId: String?) {
        if (folderId != null && folderDao.countById(folderId) == 0) return // folder deleted meanwhile
        documentDao.updateFolder(documentId, folderId)
        syncQueue.documentChanged(documentId)
    }

    override suspend fun setTags(documentId: String, tags: List<String>) {
        documentDao.updateTags(documentId, Converters().fromTags(tags.takeIf { it.isNotEmpty() }))
        syncQueue.documentChanged(documentId)
    }
}

private fun FolderEntity.toDomain() = Folder(folderId, name, type, parentFolderId)

@Module
@InstallIn(SingletonComponent::class)
abstract class FolderDataModule {
    @Binds
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
