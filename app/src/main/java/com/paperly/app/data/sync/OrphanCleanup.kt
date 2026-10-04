package com.paperly.app.data.sync

import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.model.SyncItemState
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.sync.CleanupBlock
import com.paperly.app.domain.sync.CleanupPreview
import com.paperly.app.domain.sync.CleanupResult
import com.paperly.app.domain.sync.CloudCleanup
import com.paperly.app.domain.sync.CloudFile
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.CloudListing
import com.paperly.app.domain.sync.OrphanPolicy
import com.paperly.app.domain.sync.RemoteDocument
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteResult
import com.paperly.app.domain.sync.SyncSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P8-F2: removes cloud files that belong to no document. Runs only when the user asks, only with sync idle and
 * only on a complete picture (cloud files + cloud documents + local documents); see [OrphanPolicy] for the
 * rules and the per-document re-check right before each delete.
 */
@Singleton
class OrphanCleanup @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SyncSettings,
    private val syncDao: SyncItemDao,
    private val inventory: CloudInventory,
    private val remote: RemoteMetadataStore,
    private val files: RemoteFileStore,
) : CloudCleanup {

    private sealed interface Plan {
        data class Blocked(val reason: CleanupBlock) : Plan

        data class Ready(val uid: String, val orphans: List<CloudFile>) : Plan
    }

    override suspend fun preview(now: Long): CleanupPreview = when (val outcome = plan(now)) {
        is Plan.Blocked -> CleanupPreview.Blocked(outcome.reason)
        is Plan.Ready -> CleanupPreview.Found(outcome.orphans.size, outcome.orphans.sumOf { it.sizeBytes })
    }

    override suspend fun clean(now: Long): CleanupResult = when (val outcome = plan(now)) {
        is Plan.Blocked -> CleanupResult.Blocked(outcome.reason)
        is Plan.Ready -> sweep(outcome)
    }

    private suspend fun sweep(plan: Plan.Ready): CleanupResult.Done {
        var deleted = 0
        var remaining = 0
        plan.orphans.groupBy { it.documentId }.forEach { (documentId, copies) ->
            if (removeIfStillOrphan(plan.uid, documentId)) deleted += copies.size else remaining += copies.size
        }
        return CleanupResult.Done(deleted, remaining)
    }

    /** Last look right before deleting: a document that showed up meanwhile keeps its file. */
    private suspend fun removeIfStillOrphan(uid: String, documentId: String): Boolean {
        val local = documentId in syncDao.allDocumentIds()
        val inCloud = remote.fetchDocument(uid, documentId) != RemoteDocument.Absent
        return !local && !inCloud && files.delete(documentId) == RemoteResult.OK
    }

    private suspend fun plan(now: Long): Plan {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid ?: return Plan.Blocked(CleanupBlock.NOT_SIGNED_IN)
        val early = when {
            settings.paused.first() -> CleanupBlock.PAUSED
            syncDao.getDue(SyncItemState.FAILED, Long.MAX_VALUE, 1).isNotEmpty() -> CleanupBlock.SYNC_BUSY
            else -> null
        }
        if (early != null) return Plan.Blocked(early)
        return gather(uid, now)
    }

    /**
     * Order matters: cloud files first, then cloud documents, local documents last. A document created here after
     * the cloud listing has no file in that listing; one whose file is listed already had its local row by then.
     */
    private suspend fun gather(uid: String, now: Long): Plan {
        val listing = inventory.listOwned()
        if (listing !is CloudListing.Complete) {
            val needsAccess = listing == CloudListing.NeedsConsent
            return Plan.Blocked(if (needsAccess) CleanupBlock.NEEDS_ACCESS else CleanupBlock.UNAVAILABLE)
        }
        val remoteIds = remote.listDocumentIds(uid) ?: return Plan.Blocked(CleanupBlock.UNAVAILABLE)
        val local = syncDao.allDocumentIds().toSet()
        return Plan.Ready(uid, OrphanPolicy.orphans(listing.files, local, remoteIds, now))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudCleanupModule {
    @Binds
    abstract fun bindCloudCleanup(impl: OrphanCleanup): CloudCleanup
}
