package com.paperly.app.data.backup

import android.content.Context
import android.net.Uri
import com.paperly.app.core.database.DocumentDao
import com.paperly.app.domain.backup.BackupInspector
import com.paperly.app.domain.backup.BackupPreview
import com.paperly.app.domain.backup.InspectResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One streaming pass over the file (no temp copy, no writes). Same "already present" rule as Restore. */
@Singleton
class BackupInspectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
) : BackupInspector {
    override suspend fun inspect(sourceUri: String): InspectResult = try {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: throw IOException("Cannot open")
            input.use { inspectStream(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        InspectResult.Failed
    }

    /** Reads [input] to its end and closes nothing; the caller owns the stream. */
    internal suspend fun inspectStream(input: InputStream): InspectResult = classify(BackupArchive.check(input))

    private suspend fun classify(checked: ArchiveCheck?): InspectResult = when {
        checked == null -> InspectResult.Invalid
        checked.manifest.formatVersion > BACKUP_FORMAT_VERSION -> InspectResult.NewerVersion
        !checked.isIntact ->
            InspectResult.Damaged(checked.damaged + checked.missing + checked.manifest.invalidEntries)
        else -> InspectResult.Ok(preview(checked.manifest))
    }

    private suspend fun preview(m: BackupManifest): BackupPreview {
        val present = m.documents.count {
            documentDao.getById(it.documentId) != null || documentDao.findActiveByChecksum(it.checksum) != null
        }
        return BackupPreview(
            createdAt = m.createdAt,
            appVersion = m.appVersion,
            documents = m.documents.size,
            newDocuments = m.documents.size - present,
            alreadyPresent = present,
            folders = m.folders.size,
            bookmarks = m.extras.bookmarks.size,
            annotations = m.extras.annotations.size,
            scanPages = m.extras.scanPages.size,
            unreadableEntries = m.invalidEntries + m.invalidExtras,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupInspectorModule {
    @Binds
    abstract fun bindBackupInspector(impl: BackupInspectorImpl): BackupInspector
}
