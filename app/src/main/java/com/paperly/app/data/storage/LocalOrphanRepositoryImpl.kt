package com.paperly.app.data.storage

import android.content.Context
import com.paperly.app.core.database.AggregateDao
import com.paperly.app.core.file.DOCUMENTS_DIR
import com.paperly.app.core.file.OrphanFiles
import com.paperly.app.domain.storage.LocalOrphanRepository
import com.paperly.app.domain.storage.OrphanCleanResult
import com.paperly.app.domain.storage.OrphanScan
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalOrphanRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AggregateDao,
) : LocalOrphanRepository {
    override suspend fun scan(): OrphanScan = withContext(Dispatchers.IO) {
        val found = find()
        OrphanScan(found.size, found.sumOf { it.length() })
    }

    override suspend fun clean(): OrphanCleanResult = withContext(Dispatchers.IO) {
        val found = find()
        val deleted = OrphanFiles.delete(found)
        OrphanCleanResult(deleted, found.size - deleted)
    }

    /** Folder is listed first, ids read second: a file stored (and its row inserted) in between is never a candidate. */
    private suspend fun find(): List<File> {
        val dir = File(context.filesDir, DOCUMENTS_DIR)
        val listed = OrphanFiles.candidates(dir, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(MIN_AGE_HOURS))
        return if (listed.isEmpty()) listed else OrphanFiles.unreferenced(listed, dao.getAllIds().toSet())
    }

    private companion object {
        const val MIN_AGE_HOURS = 1L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalOrphanModule {
    @Binds
    abstract fun bindLocalOrphanRepository(impl: LocalOrphanRepositoryImpl): LocalOrphanRepository
}
