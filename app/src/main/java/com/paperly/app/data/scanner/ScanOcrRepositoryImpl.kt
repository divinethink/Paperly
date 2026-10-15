package com.paperly.app.data.scanner

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.paperly.app.core.database.AggregateDao
import com.paperly.app.core.database.ScanOcrDao
import com.paperly.app.core.model.DocumentType
import com.paperly.app.domain.scanner.ScanOcrRepository
import com.paperly.app.domain.scanner.escapeLike
import com.paperly.app.domain.scanner.joinScanText
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val WORK_NAME = "scan-ocr"

@Singleton
class ScanOcrRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    private val dao: ScanOcrDao,
    private val aggregate: AggregateDao,
) : ScanOcrRepository {

    // Additive key; missing = false (OFF by default: the first use downloads the recognition data). No migration.
    override val enabled: Flow<Boolean> = store.data
        .catch { if (it is IOException) emptyFlow<Preferences>() else throw it }
        .map { it[KEY] ?: false }

    override suspend fun setEnabled(value: Boolean) {
        store.edit { it[KEY] = value }
        if (!value) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) // stored text stays
    }

    override suspend fun schedule(documentId: String) {
        if (enabled.first()) enqueue(documentId)
    }

    override suspend fun scheduleAll() {
        if (!enabled.first()) return
        aggregate.getAllActive().filter { it.type == DocumentType.SCANNED_PDF }.forEach { enqueue(it.documentId) }
    }

    override suspend fun invalidate(documentId: String) {
        dao.deleteForDocument(documentId)
        schedule(documentId)
    }

    override suspend fun text(documentId: String): String? =
        joinScanText(dao.getForDocument(documentId).map { it.pageIndex to it.text })

    override fun observeMatchingIds(query: String): Flow<Set<String>> =
        dao.observeMatching(escapeLike(query)).map { it.toSet() }

    // One chain: scans are read one after another (memory), a duplicate request only re-checks stored pages.
    private fun enqueue(documentId: String) {
        val request = OneTimeWorkRequestBuilder<ScanOcrWorker>()
            .setInputData(workDataOf(ScanOcrWorker.KEY_DOCUMENT_ID to documentId))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        val KEY = booleanPreferencesKey("scan_ocr_enabled")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanOcrModule {
    @Binds
    abstract fun bindScanOcrRepository(impl: ScanOcrRepositoryImpl): ScanOcrRepository
}
