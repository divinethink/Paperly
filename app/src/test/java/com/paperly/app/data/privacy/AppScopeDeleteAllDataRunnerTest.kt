package com.paperly.app.data.privacy

import android.content.Context
import com.paperly.app.data.sync.CleanupAuth
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.DeleteAccountResult
import com.paperly.app.domain.backup.BackupRunState
import com.paperly.app.domain.backup.BackupRunner
import com.paperly.app.domain.privacy.AccountStatus
import com.paperly.app.domain.privacy.CloudWipe
import com.paperly.app.domain.privacy.CloudWipeBlock
import com.paperly.app.domain.privacy.CloudWipeResult
import com.paperly.app.domain.privacy.DeleteOutcome
import com.paperly.app.domain.privacy.DeleteRunState
import com.paperly.app.domain.privacy.LocalWipe
import com.paperly.app.domain.sync.SyncQueue
import com.paperly.app.domain.sync.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The runner only talks to interfaces; Robolectric just supplies a Context. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppScopeDeleteAllDataRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val calls = mutableListOf<String>()

    @After
    fun tearDown() = scope.cancel()

    private inner class Cloud(var result: CloudWipeResult) : CloudWipe {
        override suspend fun wipe(): CloudWipeResult {
            calls += "cloud"
            return result
        }
    }

    private inner class Local(var fail: Boolean = false) : LocalWipe {
        override suspend fun wipe() {
            calls += "local"
            if (fail) error("boom")
        }

        override suspend fun resumeIfInterrupted() = Unit
    }

    private inner class Auth(var delete: DeleteAccountResult) : AuthRepository by CleanupAuth() {
        override suspend fun deleteAccount(): DeleteAccountResult {
            calls += "account"
            return delete
        }

        override suspend fun signOut(activity: Context) {
            calls += "signout"
        }
    }

    private inner class Settings(initial: Boolean = false) : SyncSettings {
        val pausedState = MutableStateFlow(initial)
        override val wifiOnly: Flow<Boolean> = flowOf(true)
        override val paused: Flow<Boolean> = pausedState

        override suspend fun setWifiOnly(value: Boolean) = Unit

        override suspend fun setPaused(value: Boolean) {
            pausedState.value = value
        }
    }

    private class Queue : SyncQueue {
        override suspend fun documentChanged(id: String) = Unit

        override suspend fun documentsChanged(ids: List<String>) = Unit

        override suspend fun readingChanged(id: String) = Unit

        override suspend fun readingFlush(id: String) = Unit

        override suspend fun documentDeleted(id: String) = Unit

        override suspend fun allDocumentsChanged() = Unit

        override suspend fun kick() = Unit

        override suspend fun retryFailed() = Unit

        override suspend fun resumeDeferred() = Unit
    }

    private class Backup(busy: Boolean = false) : BackupRunner {
        override val state: StateFlow<BackupRunState> = MutableStateFlow(BackupRunState(busy = busy))

        override fun startExport(targetUri: String) = Unit

        override fun startRestore(sourceUri: String) = Unit

        override fun startInspect(sourceUri: String, forRestore: Boolean) = Unit

        override fun dismissOutcome() = Unit
    }

    private fun runner(
        cloud: CloudWipe,
        local: LocalWipe = Local(),
        auth: AuthRepository = Auth(DeleteAccountResult.DELETED),
        settings: Settings = Settings(),
        backup: BackupRunner = Backup(),
    ) = AppScopeDeleteAllDataRunner(
        cloud,
        local,
        AccountWiper(auth, RuntimeEnvironment.getApplication()),
        SyncPauser(settings, Queue()),
        backup,
        scope,
    )

    private fun AppScopeDeleteAllDataRunner.finished(): DeleteRunState = runBlocking {
        withTimeout(WAIT_MS) { state.first { !it.busy && it.outcome != null } }
    }

    @Test
    fun `order is cloud then account then local`() {
        val r = runner(Cloud(CloudWipeResult.Wiped))
        r.start()
        assertEquals(DeleteOutcome.Done(true, AccountStatus.DELETED), r.finished().outcome)
        assertEquals(listOf("cloud", "account", "local"), calls)
    }

    @Test
    fun `cloud stopped leaves the phone untouched and restores sync`() {
        val settings = Settings(initial = false)
        val r = runner(Cloud(CloudWipeResult.Stopped(CloudWipeBlock.OFFLINE_OR_FAILED)), settings = settings)
        r.start()
        assertEquals(DeleteOutcome.CloudStopped(CloudWipeBlock.OFFLINE_OR_FAILED), r.finished().outcome)
        assertEquals(listOf("cloud"), calls)
        assertFalse(settings.pausedState.value)
    }

    @Test
    fun `previously paused sync stays paused after a stop`() {
        val settings = Settings(initial = true)
        val r = runner(Cloud(CloudWipeResult.Stopped(CloudWipeBlock.DENIED)), settings = settings)
        r.start()
        r.finished()
        assertEquals(true, settings.pausedState.value)
    }

    @Test
    fun `not signed in skips cloud account steps but wipes the phone`() {
        val r = runner(Cloud(CloudWipeResult.NotSignedIn))
        r.start()
        assertEquals(DeleteOutcome.Done(false, AccountStatus.NONE), r.finished().outcome)
        assertEquals(listOf("cloud", "local"), calls)
    }

    @Test
    fun `account that cannot be deleted is signed out and reported`() {
        val r = runner(Cloud(CloudWipeResult.Wiped), auth = Auth(DeleteAccountResult.NEEDS_RECENT_LOGIN))
        r.start()
        assertEquals(DeleteOutcome.Done(true, AccountStatus.KEPT), r.finished().outcome)
        assertEquals(listOf("cloud", "account", "signout", "local"), calls)
    }

    @Test
    fun `local failure is reported separately`() {
        val r = runner(Cloud(CloudWipeResult.Wiped), local = Local(fail = true))
        r.start()
        assertEquals(DeleteOutcome.LocalFailed(true), r.finished().outcome)
    }

    @Test
    fun `refuses to start while a backup runs`() {
        val r = runner(Cloud(CloudWipeResult.Wiped), backup = Backup(busy = true))
        r.start()
        Thread.sleep(SETTLE_MS)
        assertEquals(DeleteRunState(), r.state.value)
        assertEquals(emptyList<String>(), calls)
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 100L
    }
}
