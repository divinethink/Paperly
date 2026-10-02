package com.paperly.app.data.storage

import com.paperly.app.core.database.AggregateDao
import com.paperly.app.domain.storage.StorageUsage
import com.paperly.app.domain.storage.StorageUsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class StorageUsageRepositoryImpl @Inject constructor(
    private val dao: AggregateDao,
) : StorageUsageRepository {
    override fun observeUsage(): Flow<StorageUsage> = dao.observeStorageUsage().map {
        StorageUsage(it.activeBytes, it.activeCount, it.trashBytes, it.trashCount)
    }

    override fun observeDuplicateGroups(): Flow<List<List<String>>> = dao.observeDuplicateRows().map { rows ->
        rows.groupBy { it.checksum }.values.map { group -> group.map { it.documentId } }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageDataModule {
    @Binds
    abstract fun bindStorageUsageRepository(impl: StorageUsageRepositoryImpl): StorageUsageRepository
}
