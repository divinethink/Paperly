package com.paperly.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

/** WorkManager entry for the sync queue. Hilt via EntryPoint (no hilt-work dependency, same as ImageExportWorker). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun processor(): SyncProcessor
    }

    override suspend fun doWork(): Result = try {
        val processor = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java).processor()
        when (processor.run()) {
            SyncRunResult.WAIT -> Result.retry()
            SyncRunResult.DONE, SyncRunResult.NOT_SIGNED_IN, SyncRunResult.PAUSED -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry() // never crash the app from background sync; the queue is intact
    }
}
