package com.paperly.app.data.reader

import com.paperly.app.core.database.AnnotationDao
import com.paperly.app.core.database.AnnotationEntity
import com.paperly.app.domain.reader.AnnotationColor
import com.paperly.app.domain.reader.AnnotationContent
import com.paperly.app.domain.reader.AnnotationType
import com.paperly.app.domain.reader.EpubAnnotation
import com.paperly.app.domain.reader.EpubAnnotationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Shares the PDF `annotations` table (no schema change): EPUB rows = JSON locator + zero rect; PDF = page int. */
@Singleton
class EpubAnnotationRepositoryImpl @Inject constructor(
    private val dao: AnnotationDao,
) : EpubAnnotationRepository {

    override fun observe(documentId: String): Flow<List<EpubAnnotation>> =
        dao.observeForDocument(documentId).map { rows -> rows.mapNotNull { it.toEpub() } }

    override suspend fun add(documentId: String, locatorJson: String, content: AnnotationContent) {
        val now = System.currentTimeMillis()
        dao.insert(
            AnnotationEntity(
                annotationId = UUID.randomUUID().toString(),
                documentId = documentId,
                locator = locatorJson,
                type = content.type.key,
                color = content.color?.key?.takeIf { content.type != AnnotationType.NOTE },
                rectLeft = 0f,
                rectTop = 0f,
                rectRight = 0f,
                rectBottom = 0f,
                noteText = content.noteText?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: String, content: AnnotationContent) {
        val color = content.color?.key?.takeIf { content.type != AnnotationType.NOTE }
        val note = content.noteText?.trim()?.takeIf { it.isNotEmpty() }
        dao.update(id, content.type.key, color, note, System.currentTimeMillis())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun AnnotationEntity.toEpub(): EpubAnnotation? {
        if (!locator.startsWith("{")) return null // PDF row (page index)
        val kind = AnnotationType.fromKey(type) ?: return null
        return EpubAnnotation(annotationId, locator, kind, AnnotationColor.fromKey(color), noteText)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EpubAnnotationDataModule {
    @Binds
    abstract fun bindEpubAnnotationRepository(impl: EpubAnnotationRepositoryImpl): EpubAnnotationRepository
}
