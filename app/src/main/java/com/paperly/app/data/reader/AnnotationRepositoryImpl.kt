package com.paperly.app.data.reader

import com.paperly.app.core.database.AnnotationDao
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationRepository
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.MatchRect
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
class AnnotationRepositoryImpl @Inject constructor(
    private val dao: AnnotationDao,
) : AnnotationRepository {

    override fun observe(documentId: String): Flow<List<Annotation>> =
        dao.observeForDocument(documentId).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun add(documentId: String, page: Int, type: AnnotationType, rect: MatchRect, noteText: String?) {
        if (page < 0) return
        val now = System.currentTimeMillis()
        dao.insert(
            AnnotationEntity(
                annotationId = UUID.randomUUID().toString(),
                documentId = documentId,
                locator = page.toString(),
                type = type.key,
                rectLeft = rect.left.coerceIn(0f, 1f),
                rectTop = rect.top.coerceIn(0f, 1f),
                rectRight = rect.right.coerceIn(0f, 1f),
                rectBottom = rect.bottom.coerceIn(0f, 1f),
                noteText = noteText?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: String, type: AnnotationType, noteText: String?) {
        dao.update(id, type.key, noteText?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun AnnotationEntity.toDomain(): Annotation? {
        val kind = AnnotationType.fromKey(type) ?: return null
        val page = locator.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Annotation(annotationId, page, kind, MatchRect(rectLeft, rectTop, rectRight, rectBottom), noteText)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnotationDataModule {
    @Binds
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository
}
