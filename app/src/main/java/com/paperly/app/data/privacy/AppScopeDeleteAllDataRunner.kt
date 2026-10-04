package com.paperly.app.data.privacy

import com.paperly.app.core.di.ApplicationScope
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.privacy.AccountStatus
import com.paperly.app.domain.privacy.CloudWipe
import com.paperly.app.domain.privacy.CloudWipeBlock
import com.paperly.app.domain.privacy.CloudWipeResult
import com.paperly.app.domain.privacy.DeleteAllDataRunner
import com.paperly.app.domain.privacy.DeleteOutcome
import com.paperly.app.domain.privacy.DeleteRunState
import com.paperly.app.domain.privacy.DeleteStep
import com.paperly.app.domain.privacy.LocalWipe
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
class AppScopeDeleteAllDataRunner @Inject constructor(
    private val cloud: CloudWipe,
    private val local: LocalWipe,
    private val account: AccountWiper,
    private val pauser: SyncPauser,
    private val backup: BackupRunner,
    @ApplicationScope private val scope: CoroutineScope,
) : DeleteAllDataRunner {
    private val _state = MutableStateFlow(DeleteRunState())
    override val state: StateFlow<DeleteRunState> = _state.asStateFlow()

    override fun start() {
        if (backup.state.value.busy) return
        while (true) {
            val current = _state.value
            if (current.busy) return
            if (_state.compareAndSet(current, DeleteRunState(step = DeleteStep.CLOUD))) break
        }
        scope.launch {
            var outcome: DeleteOutcome? = null
            try {
                outcome = execute()
            } finally {
                // busy can never get stuck, even if the scope is cancelled mid-way.
                _state.value = DeleteRunState(outcome = outcome)
            }
        }
    }

    override fun dismissOutcome() = _state.update { if (it.busy) it else DeleteRunState() }

    private suspend fun execute(): DeleteOutcome {
        val wasPaused = guarded(false) { pauser.pause() } // no sync run may re-upload while the cloud is emptied
        val failed = CloudWipeResult.Stopped(CloudWipeBlock.OFFLINE_OR_FAILED)
        val result = guarded<CloudWipeResult>(failed) { cloud.wipe() }
        if (result is CloudWipeResult.Stopped) {
            guarded(Unit) { pauser.restore(wasPaused) } // nothing was deleted here: sync back as the user had it
            return DeleteOutcome.CloudStopped(result.reason)
        }
        val cloudWiped = result is CloudWipeResult.Wiped
        val account = if (cloudWiped) deleteAccount() else AccountStatus.NONE
        _state.update { it.copy(step = DeleteStep.LOCAL) }
        val localOk = guarded(false) {
            local.wipe()
            true
        }
        return if (localOk) DeleteOutcome.Done(cloudWiped, account) else DeleteOutcome.LocalFailed(cloudWiped)
    }

    private suspend fun deleteAccount(): AccountStatus {
        _state.update { it.copy(step = DeleteStep.ACCOUNT) }
        return guarded(AccountStatus.KEPT) { account.deleteOrSignOut() }
    }

    private suspend fun <T> guarded(fallback: T, call: suspend () -> T): T = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }
}
