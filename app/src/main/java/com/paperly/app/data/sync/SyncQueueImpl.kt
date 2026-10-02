package com.paperly.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.sync.SyncQueue
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Queue writes + WorkManager scheduling. Scheduling uses REPLACE with a short delay, so a burst of edits
 * (batch actions) collapses into one run; a run cancelled by REPLACE is safe because items are only
 * completed by version and a half-done item is picked up again.
 */
@Singleton
class SyncQueueImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SyncItemDao,
) : SyncQueue {

    override suspend fun documentChanged(id: String) = guarded {
        dao.enqueue(SyncEntityType.DOCUMENT, id, SyncOperation.PUT, System.currentTimeMillis())
    }

    override suspend fun documentsChanged(ids: List<String>) = guarded {
        ids.forEach { dao.enqueue(SyncEntityType.DOCUMENT, it, SyncOperation.PUT, System.currentTimeMillis()) }
    }

    override suspend fun documentDeleted(id: String) = guarded {
        dao.enqueue(SyncEntityType.DOCUMENT, id, SyncOperation.DELETE, System.currentTimeMillis())
    }

    override suspend fun allDocumentsChanged() = guarded {
        dao.enqueueAllDocuments(System.currentTimeMillis())
    }

    override suspend fun kick() = guarded { }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
            schedule()
        } catch (e: CancellationException) {
            throw e
        } catch (ignored: Exception) {
            // best effort by design (see SyncQueue): the library write that triggered this has already succeeded
        }
    }

    private fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "sync"
        const val DEBOUNCE_SECONDS = 3L
        const val BACKOFF_SECONDS = 30L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncQueueModule {
    @Binds
    abstract fun bindSyncQueue(impl: SyncQueueImpl): SyncQueue
}
