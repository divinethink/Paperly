package com.paperly.app.data.backup

import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.backup.BackupInspector
import com.paperly.app.domain.backup.BackupOutcome
import com.paperly.app.domain.backup.BackupRepository
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunState
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.backup.InspectResult
import com.paperly.app.domain.backup.RestoreResult
import com.paperly.app.domain.sync.SyncQueue
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class AppScopeBackupRunner @Inject constructor(
    private val repository: BackupRepository,
    private val inspector: BackupInspector,
    private val syncQueue: SyncQueue,
    @ApplicationScope private val scope: CoroutineScope,
) : BackupRunner {
    private val _state = MutableStateFlow(BackupRunState())
    override val state: StateFlow<BackupRunState> = _state.asStateFlow()

    override fun startExport(targetUri: String) = start {
        BackupOutcome.Exported(
            guarded(BackupResult.Failed) { repository.export(targetUri) },
        )
    }

    override fun startRestore(sourceUri: String) = start {
        val result = guarded(RestoreResult.Failed) { repository.restore(sourceUri) }
        if (result is RestoreResult.Done && result.summary.restored > 0) {
            // Best effort: the documents are already restored; a queue problem must not turn that into a failure.
            guarded(Unit) { syncQueue.allDocumentsChanged() }
        }
        BackupOutcome.Restored(result)
    }

    override fun startInspect(sourceUri: String, forRestore: Boolean) = start {
        BackupOutcome.Inspected(sourceUri, guarded(InspectResult.Failed) { inspector.inspect(sourceUri) }, forRestore)
    }

    override fun dismissOutcome() = _state.update { if (it.busy) it else BackupRunState() }

    /** Only one caller can flip idle -> busy (atomic), so a double-tap never starts a second operation. */
    private fun start(block: suspend () -> BackupOutcome) {
        while (true) {
            val current = _state.value
            if (current.busy) return
            if (_state.compareAndSet(current, BackupRunState(busy = true))) break
        }
        scope.launch {
            var outcome: BackupOutcome? = null
            try {
                outcome = block()
            } finally {
                // busy can never get stuck, even if the scope is cancelled mid-way.
                _state.value = BackupRunState(outcome = outcome)
            }
        }
    }

    private suspend fun <T> guarded(fallback: T, call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupRunnerModule {
    @Binds
    abstract fun bindBackupRunner(impl: AppScopeBackupRunner): BackupRunner
}
