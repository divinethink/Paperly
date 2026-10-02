package com.paperly.app.data.reader

import com.paperly.app.core.database.BookmarkEntity
import com.paperly.app.core.database.ReaderDao
import com.paperly.app.core.database.ReadingStateEntity
import com.paperly.app.domain.reader.ReaderStateRepository
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
class ReaderStateRepositoryImpl @Inject constructor(
    private val dao: ReaderDao,
) : ReaderStateRepository {

    override suspend fun getSavedPage(documentId: String): Int? =
        dao.getState(documentId)?.locator?.toIntOrNull()?.takeIf { it >= 0 }

    override suspend fun saveProgress(documentId: String, page: Int, pageCount: Int) {
        if (page < 0 || pageCount <= 0) return
        val percent = ((page + 1).toFloat() / pageCount).coerceIn(0f, 1f)
        dao.upsertState(ReadingStateEntity(documentId, page.toString(), percent, System.currentTimeMillis()))
    }

    override suspend fun getSavedLocator(documentId: String): String? = dao.getState(documentId)?.locator

    override suspend fun saveLocator(documentId: String, locator: String, progress: Float) {
        dao.upsertState(ReadingStateEntity(documentId, locator, progress.coerceIn(0f, 1f), System.currentTimeMillis()))
    }

    override fun observeBookmarkLocators(documentId: String): Flow<List<String>> =
        dao.observeBookmarkLocators(documentId)

    override suspend fun toggleBookmarkLocator(documentId: String, locator: String): Boolean =
        dao.toggleBookmark(
            BookmarkEntity(
                bookmarkId = UUID.randomUUID().toString(),
                documentId = documentId,
                locator = locator,
                createdAt = System.currentTimeMillis(),
            ),
        )

    override fun observeProgress(documentId: String): Flow<Float?> = dao.observeProgress(documentId)

    override fun observeBookmarkedPages(documentId: String): Flow<Set<Int>> =
        dao.observeBookmarkLocators(documentId).map { list -> list.mapNotNull { it.toIntOrNull() }.toSet() }

    override suspend fun toggleBookmark(documentId: String, page: Int): Boolean =
        dao.toggleBookmark(
            BookmarkEntity(
                bookmarkId = UUID.randomUUID().toString(),
                documentId = documentId,
                locator = page.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderStateDataModule {
    @Binds
    abstract fun bindReaderStateRepository(impl: ReaderStateRepositoryImpl): ReaderStateRepository
}
