package com.paperly.app.data.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

/** WorkManager entry for scan OCR (background, not foreground). Hilt via EntryPoint, like SyncWorker. */
class ScanOcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun runner(): ScanOcrRunner
    }

    override suspend fun doWork(): Result = try {
        val id = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        val runner = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java).runner()
        if (runner.run(id) == ScanOcrRun.RETRY) retryOrGiveUp() else Result.success()
    } catch (e: CancellationException) {
        throw e // stopped or replaced: finished pages are already stored, the next run continues
    } catch (e: Exception) {
        retryOrGiveUp() // never crash the app from background OCR; the scan itself is untouched
    }

    // A page that keeps failing must not loop forever: after a few tries the rest stays unread.
    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()

    companion object {
        const val KEY_DOCUMENT_ID = "documentId"
        private const val MAX_ATTEMPTS = 3
    }
}
