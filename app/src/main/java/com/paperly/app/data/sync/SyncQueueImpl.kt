package com.paperly.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paperly.app.core.database.CloudSyncDao
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.model.SyncEntityType
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.core.model.SyncOperation
import com.paperly.app.domain.sync.ReadingThrottle
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
import kotlinx.coroutines.flow.first

/**
 * Queue writes + WorkManager scheduling. Scheduling uses REPLACE with a short delay, so a burst of edits
 * (batch actions) collapses into one run; a run cancelled by REPLACE is safe because items are only
 * completed by version and a half-done item is picked up again.
 */
@Suppress("TooManyFunctions") // the SyncQueue surface (one method per kind of change) + two private helpers
@Singleton
class SyncQueueImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: SyncItemDao,
    private val cloud: CloudSyncDao,
    private val settings: DataStore<Preferences>,
) : SyncQueue {
    private val readingThrottle = ReadingThrottle()

    override suspend fun documentChanged(id: String) = guarded {
        dao.enqueue(SyncEntityType.DOCUMENT, id, SyncOperation.PUT, System.currentTimeMillis())
    }

    override suspend fun documentsChanged(ids: List<String>) = guarded {
        ids.forEach { dao.enqueue(SyncEntityType.DOCUMENT, it, SyncOperation.PUT, System.currentTimeMillis()) }
    }

    override suspend fun readingChanged(id: String) {
        if (readingThrottle.onChange(id)) enqueueReading(id)
    }

    override suspend fun readingFlush(id: String) {
        if (readingThrottle.onFlush(id)) enqueueReading(id)
    }

    private suspend fun enqueueReading(id: String) = guarded {
        dao.enqueue(SyncEntityType.READING, id, SyncOperation.PUT, System.currentTimeMillis())
    }

    override suspend fun documentDeleted(id: String) = guarded {
        val now = System.currentTimeMillis()
        dao.enqueue(SyncEntityType.DOCUMENT, id, SyncOperation.DELETE, now)
        dao.enqueue(SyncEntityType.READING, id, SyncOperation.DELETE, now)
        // The cloud file goes too (no row needed: Drive finds it by document id); replaces a pending upload.
        dao.enqueue(SyncEntityType.FILE, id, SyncOperation.DELETE, now)
    }

    override suspend fun allDocumentsChanged() = guarded {
        val now = System.currentTimeMillis()
        dao.enqueueAllDocuments(now)
        dao.enqueueAllReading(now)
    }

    override suspend fun kick() = guarded { }

    override suspend fun retryFailed() = guarded { dao.retryFailed(System.currentTimeMillis()) }

    override suspend fun resumeDeferred() = guarded {
        cloud.releaseDeferred(SyncEntityType.FILE, SyncItemState.RETRYING, SYNC_ERROR_NEEDS_ACCESS)
    }

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

    /** Paused = nothing scheduled (the queue stays). Wi-Fi only = the run waits for an unmetered network. */
    private suspend fun schedule() {
        val prefs = settings.data.first()
        val work = WorkManager.getInstance(context)
        if (prefs[SyncPrefs.PAUSED] == true) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val network = if (prefs[SyncPrefs.WIFI_ONLY] ?: true) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
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
