package com.paperly.app.data.scanner

import com.paperly.app.core.database.ScanPageDao
import com.paperly.app.core.database.ScanPageEntity
import com.paperly.app.domain.scanner.MAX_PAGE_NOTE
import com.paperly.app.domain.scanner.MAX_PAGE_TITLE
import com.paperly.app.domain.scanner.ScanPageInfo
import com.paperly.app.domain.scanner.ScanPageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ScanPageRepositoryImpl @Inject constructor(private val dao: ScanPageDao) : ScanPageRepository {
    override fun observe(documentId: String, pageIndex: Int): Flow<ScanPageInfo> =
        dao.observe(documentId, pageIndex).map { ScanPageInfo(it?.title.orEmpty(), it?.note.orEmpty()) }

    override suspend fun save(documentId: String, pageIndex: Int, info: ScanPageInfo) {
        val title = info.title.trim().take(MAX_PAGE_TITLE)
        val note = info.note.trim().take(MAX_PAGE_NOTE)
        if (title.isEmpty() && note.isEmpty()) {
            dao.delete(documentId, pageIndex)
        } else {
            val now = System.currentTimeMillis()
            dao.upsert(ScanPageEntity(documentId, pageIndex, title.ifEmpty { null }, note.ifEmpty { null }, now))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanPageDataModule {
    @Binds
    abstract fun bindScanPageRepository(impl: ScanPageRepositoryImpl): ScanPageRepository
}
