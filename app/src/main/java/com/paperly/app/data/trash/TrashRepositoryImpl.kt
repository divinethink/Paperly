package com.paperly.app.data.trash

import com.paperly.app.core.database.TrashDao
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.data.document.toDomain
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.trash.TrashRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TrashRepositoryImpl @Inject constructor(
    private val dao: TrashDao,
    private val fileStore: DocumentFileStore,
) : TrashRepository {

    override fun observeTrash(): Flow<List<Document>> =
        dao.observeTrashed().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun trash(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun restore(id: String) {
        dao.restore(id, System.currentTimeMillis())
    }

    /** File first, row second: a failure between them leaves a retryable Trash row, never an invisible orphan file. */
    override suspend fun deleteForever(id: String): Boolean {
        if (dao.countTrashed(id) == 0) return false // never touch a document that is not in Trash
        val fileGone = withContext(Dispatchers.IO) { fileStore.resolve(id) == null } || fileStore.delete(id)
        return fileGone && dao.deleteTrashedRow(id) > 0
    }

    override suspend fun emptyTrash(): Boolean = dao.getTrashedIds().map { deleteForever(it) }.all { it }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TrashDataModule {
    @Binds
    abstract fun bindTrashRepository(impl: TrashRepositoryImpl): TrashRepository
}
