package com.paperly.app.data.backup

import com.paperly.app.domain.backup.BackupInspector
import com.paperly.app.domain.backup.BackupOutcome
import com.paperly.app.domain.backup.BackupRepository
import com.paperly.app.domain.backup.BackupResult
import com.paperly.app.domain.backup.BackupRunState
import com.paperly.app.domain.backup.InspectResult
import com.paperly.app.domain.backup.RestoreResult
import com.paperly.app.domain.backup.RestoreSummary
import com.paperly.app.domain.sync.SyncQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM test: the runner has no Android dependency. */
class AppScopeBackupRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private class FakeRepo : BackupRepository {
        val gate = CompletableDeferred<Unit>()
        val exportCalls = AtomicInteger()
        val restoreCalls = AtomicInteger()
        var exportResult: BackupResult = BackupResult.Success(2, 0)
        var restoreResult: RestoreResult = RestoreResult.Done(RestoreSummary(1, 0, 0))
        var throwOnCall = false

        override suspend fun export(targetUri: String): BackupResult {
            exportCalls.incrementAndGet()
            gate.await()
            if (throwOnCall) error("boom")
            return exportResult
        }

        override suspend fun restore(sourceUri: String): RestoreResult {
            restoreCalls.incrementAndGet()
            gate.await()
            if (throwOnCall) error("boom")
            return restoreResult
        }
    }

    private class FakeInspector : BackupInspector {
        val calls = AtomicInteger()
        var result: InspectResult = InspectResult.Invalid
        var throwOnCall = false

        override suspend fun inspect(sourceUri: String): InspectResult {
            calls.incrementAndGet()
            if (throwOnCall) error("boom")
            return result
        }
    }

    private class FakeQueue(private val fail: Boolean = false) : SyncQueue {
        val allChanged = AtomicInteger()
        override suspend fun documentChanged(id: String) = Unit
        override suspend fun documentsChanged(ids: List<String>) = Unit
        override suspend fun readingChanged(id: String) = Unit
        override suspend fun readingFlush(id: String) = Unit
        override suspend fun documentDeleted(id: String) = Unit
        override suspend fun allDocumentsChanged() {
            allChanged.incrementAndGet()
            if (fail) error("queue down")
        }
        override suspend fun kick() = Unit
        override suspend fun retryFailed() = Unit
        override suspend fun resumeDeferred() = Unit
    }

    private fun runner(repo: FakeRepo, queue: FakeQueue = FakeQueue(), inspector: FakeInspector = FakeInspector()) =
        AppScopeBackupRunner(repo, inspector, queue, scope)

    private fun AppScopeBackupRunner.finished(): BackupRunState = runBlocking {
        withTimeout(5_000) { state.first { !it.busy && it.outcome != null } }
    }

    @Test
    fun startsIdleWithNoOutcome() {
        assertEquals(BackupRunState(), runner(FakeRepo()).state.value)
    }

    @Test
    fun busyImmediatelyAndSecondStartIsIgnored() {
        val repo = FakeRepo()
        val r = runner(repo)
        r.startExport("content://a")
        assertTrue(r.state.value.busy)
        r.startExport("content://b")
        r.startRestore("content://c")
        repo.gate.complete(Unit)
        val done = r.finished()
        assertEquals(BackupOutcome.Exported(BackupResult.Success(2, 0)), done.outcome)
        assertEquals(1, repo.exportCalls.get())
        assertEquals(0, repo.restoreCalls.get())
    }

    @Test
    fun repositoryExceptionBecomesFailureAndNeverLeavesItStuckBusy() {
        val repo = FakeRepo().apply { throwOnCall = true }
        val r = runner(repo)
        r.startExport("content://a")
        repo.gate.complete(Unit)
        assertEquals(BackupOutcome.Exported(BackupResult.Failed), r.finished().outcome)

        r.startRestore("content://b")
        assertEquals(BackupOutcome.Restored(RestoreResult.Failed), r.finished().outcome)
    }

    @Test
    fun dismissClearsFinishedOutcomeButNotARunningOperation() {
        val repo = FakeRepo()
        val r = runner(repo)
        r.startExport("content://a")
        r.dismissOutcome()
        assertTrue(r.state.value.busy)
        repo.gate.complete(Unit)
        r.finished()
        r.dismissOutcome()
        assertEquals(BackupRunState(), r.state.value)
    }

    @Test
    fun newStartClearsThePreviousOutcome() {
        val repo = FakeRepo().apply { gate.complete(Unit) }
        val r = runner(repo)
        r.startExport("content://a")
        r.finished()
        r.startRestore("content://b")
        // the old Exported outcome is gone the moment the new operation starts
        assertFalse(r.state.value.outcome is BackupOutcome.Exported)
        assertEquals(BackupOutcome.Restored(repo.restoreResult), r.finished().outcome)
    }

    @Test
    fun restoreQueuesSyncOnlyWhenSomethingWasRestored() {
        val repo = FakeRepo().apply { gate.complete(Unit) }
        val queue = FakeQueue()
        val r = runner(repo, queue)

        repo.restoreResult = RestoreResult.Done(RestoreSummary(0, 3, 0))
        r.startRestore("content://a")
        r.finished()
        assertEquals(0, queue.allChanged.get())

        r.dismissOutcome()
        repo.restoreResult = RestoreResult.Done(RestoreSummary(2, 0, 0))
        r.startRestore("content://b")
        r.finished()
        assertEquals(1, queue.allChanged.get())
    }

    @Test
    fun syncQueueProblemDoesNotTurnARestoreIntoAFailure() {
        val repo = FakeRepo().apply { gate.complete(Unit) }
        val queue = FakeQueue(fail = true)
        val r = runner(repo, queue)
        r.startRestore("content://a")
        val outcome = r.finished().outcome
        assertEquals(BackupOutcome.Restored(repo.restoreResult), outcome)
        assertEquals(1, queue.allChanged.get())
        assertFalse(r.state.value.busy)
    }

    @Test
    fun inspectIsReadOnlyAndKeepsTheUriForTheConfirmStep() {
        val repo = FakeRepo()
        val inspector = FakeInspector().apply { result = InspectResult.Damaged(2) }
        val queue = FakeQueue()
        val r = runner(repo, queue, inspector)
        r.startInspect("content://picked", forRestore = true)
        val done = r.finished()
        assertEquals(BackupOutcome.Inspected("content://picked", InspectResult.Damaged(2), true), done.outcome)
        assertEquals(1, inspector.calls.get())
        assertEquals(0, repo.restoreCalls.get())
        assertEquals(0, repo.exportCalls.get())
        assertEquals(0, queue.allChanged.get())
    }

    @Test
    fun inspectorExceptionBecomesFailedAndNeverLeavesItStuckBusy() {
        val inspector = FakeInspector().apply { throwOnCall = true }
        val r = runner(FakeRepo(), inspector = inspector)
        r.startInspect("content://x", forRestore = false)
        assertEquals(BackupOutcome.Inspected("content://x", InspectResult.Failed, false), r.finished().outcome)
    }

    @Test
    fun inspectWhileAnExportRunsIsIgnored() {
        val repo = FakeRepo()
        val inspector = FakeInspector()
        val r = runner(repo, inspector = inspector)
        r.startExport("content://a")
        r.startInspect("content://b", forRestore = true)
        repo.gate.complete(Unit)
        r.finished()
        assertEquals(0, inspector.calls.get())
    }
}
