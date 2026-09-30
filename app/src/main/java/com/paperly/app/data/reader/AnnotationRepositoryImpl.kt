package com.paperly.app.data.reader

import com.paperly.app.core.database.AnnotationDao
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.domain.reader.Annotation
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
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

    override suspend fun add(documentId: String, page: Int, rect: MatchRect, content: AnnotationContent) {
        if (page < 0) return
        val now = System.currentTimeMillis()
        dao.insert(
            AnnotationEntity(
                annotationId = UUID.randomUUID().toString(),
                documentId = documentId,
                locator = page.toString(),
                type = content.type.key,
                color = content.storedColor(),
                rectLeft = rect.left.coerceIn(0f, 1f),
                rectTop = rect.top.coerceIn(0f, 1f),
                rectRight = rect.right.coerceIn(0f, 1f),
                rectBottom = rect.bottom.coerceIn(0f, 1f),
                noteText = content.storedNote(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: String, content: AnnotationContent) {
        dao.update(id, content.type.key, content.storedColor(), content.storedNote(), System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun AnnotationContent.storedColor(): String? = color?.key?.takeIf { type != AnnotationType.NOTE }

    private fun AnnotationContent.storedNote(): String? = noteText?.trim()?.takeIf { it.isNotEmpty() }

    private fun AnnotationEntity.toDomain(): Annotation? {
        val kind = AnnotationType.fromKey(type) ?: return null
        val page = locator.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val rect = MatchRect(rectLeft, rectTop, rectRight, rectBottom)
        return Annotation(annotationId, page, kind, rect, noteText, AnnotationColor.fromKey(color))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnotationDataModule {
    @Binds
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository
}
