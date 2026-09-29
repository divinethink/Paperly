package com.paperly.app.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.core.database.DocumentEntity
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.document.Document
import com.paperly.app.domain.document.DocumentRepository
import com.paperly.app.domain.document.ImportResult
import com.paperly.app.domain.document.MAX_TITLE_LENGTH
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentDao,
    private val fileStore: DocumentFileStore,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeActive().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getDocument(id: String): Document? =
        dao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun importDocument(sourceUri: String, allowDuplicate: Boolean): ImportResult {
        val uri = Uri.parse(sourceUri)
        val (name, mime) = withContext(Dispatchers.IO) { queryName(uri) to context.contentResolver.getType(uri) }
        val type = detectType(name, mime) ?: return ImportResult.UnsupportedType

        val id = UUID.randomUUID().toString()
        val stored = try {
            fileStore.store(uri, id)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportResult.Failed
        }

        val now = System.currentTimeMillis() // UTC epoch millis
        val entity = DocumentEntity(
            documentId = id,
            title = name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Untitled",
            type = type,
            sizeBytes = stored.sizeBytes,
            checksum = stored.checksum,
            localUri = stored.path,
            createdAt = now,
            updatedAt = now,
        )
        return try {
            val existing = if (allowDuplicate) null else dao.findActiveByChecksum(stored.checksum)
            when {
                existing != null -> {
                    cleanup(id) // discard the just-copied file; the original stays untouched
                    ImportResult.Duplicate(existing.documentId, existing.title)
                }
                dao.insert(entity) == -1L -> {
                    cleanup(id)
                    ImportResult.Failed
                }
                else -> ImportResult.Success(id)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            cleanup(id)
            throw e
        } catch (e: Exception) {
            cleanup(id) // no orphan file if the DB write failed
            ImportResult.Failed
        }
    }

    override suspend fun renameDocument(id: String, newTitle: String): Boolean {
        val title = newTitle.trim().take(MAX_TITLE_LENGTH)
        return title.isNotEmpty() && dao.updateTitle(id, title, System.currentTimeMillis()) > 0
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.updateFavorite(id, favorite) // idempotent; deliberately no updatedAt bump (keeps list order stable)
    }

    override suspend fun markOpened(id: String) {
        dao.updateLastOpened(id, System.currentTimeMillis())
    }

    private suspend fun cleanup(id: String) {
        withContext(NonCancellable) { fileStore.delete(id) }
    }

    private fun queryName(uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun detectType(name: String?, mime: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> DocumentType.PDF
            ext == "epub" || mime == "application/epub+zip" -> DocumentType.EPUB
            else -> null
        }
    }
}

/** Mapper with runtime validation: rows with an unknown type are skipped, not crashed on. */
internal fun DocumentEntity.toDomain(): Document? =
    if (DocumentType.isValid(type)) {
        Document(documentId, title, type, sizeBytes, createdAt, isFavorite, lastOpenedAt, folderId, tags.orEmpty())
    } else {
        null
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentDataModule {
    @Binds
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository
}
