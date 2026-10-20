package com.paperly.app.data.privacy

import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.privacy.DeleteAllDataRunner
import com.paperly.app.domain.privacy.DeleteScope
import com.paperly.app.domain.privacy.LocalTypeWipe
import com.paperly.app.domain.privacy.ScopedDeleteOutcome
import com.paperly.app.domain.privacy.ScopedDeleteRunner
import com.paperly.app.domain.privacy.ScopedDeleteState
import com.paperly.app.domain.trash.TrashRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class AppScopeScopedDeleteRunner @Inject constructor(
    private val localWipe: LocalTypeWipe,
    private val trash: TrashRepository,
    private val backup: BackupRunner,
    private val deleteAll: DeleteAllDataRunner,
    @ApplicationScope private val appScope: CoroutineScope,
) : ScopedDeleteRunner {
    private val _state = MutableStateFlow(ScopedDeleteState())
    override val state: StateFlow<ScopedDeleteState> = _state.asStateFlow()

    override fun start(scope: DeleteScope) {
        if (scope == DeleteScope.ALL || backup.state.value.busy || deleteAll.state.value.busy) return
        while (true) {
            val current = _state.value
            if (current.running) return
            if (_state.compareAndSet(current, ScopedDeleteState(running = true))) break
        }
        appScope.launch {
            var outcome: ScopedDeleteOutcome? = null
            try {
                outcome = execute(scope)
            } finally {
                // running can never get stuck, even if the scope is cancelled mid-way.
                _state.value = ScopedDeleteState(outcome = outcome)
            }
        }
    }

    override fun dismissOutcome() = _state.update { if (it.running) it else ScopedDeleteState() }

    private suspend fun execute(scope: DeleteScope): ScopedDeleteOutcome = try {
        val type = scope.type
        if (type != null) localWipe.wipe(type) else emptyTrash()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ScopedDeleteOutcome.Failed
    }

    /** Same as "Empty Trash" in the Trash screen (that one also removes cloud copies of trashed documents). */
    private suspend fun emptyTrash(): ScopedDeleteOutcome {
        val before = trash.observeTrash().first().size
        trash.emptyTrash()
        val left = trash.observeTrash().first().size
        return if (left == 0) ScopedDeleteOutcome.Done(before) else ScopedDeleteOutcome.Partial(before - left, left)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScopedDeleteModule {
    @Binds
    abstract fun bindLocalTypeWipe(impl: LocalTypeWipeImpl): LocalTypeWipe

    @Binds
    abstract fun bindScopedDeleteRunner(impl: AppScopeScopedDeleteRunner): ScopedDeleteRunner
}
