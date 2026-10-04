package com.paperly.app.data.privacy

import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.domain.auth.AuthRepository
import com.paperly.app.domain.auth.AuthState
import com.paperly.app.domain.privacy.CloudWipe
import com.paperly.app.domain.privacy.CloudWipeBlock
import com.paperly.app.domain.privacy.CloudWipeResult
import com.paperly.app.domain.privacy.DeleteAllDataRunner
import com.paperly.app.domain.privacy.LocalWipe
import com.paperly.app.domain.sync.CloudInventory
import com.paperly.app.domain.sync.CloudListing
import com.paperly.app.domain.sync.RemoteFileStore
import com.paperly.app.domain.sync.RemoteMetadataStore
import com.paperly.app.domain.sync.RemoteReadingStore
import com.paperly.app.domain.sync.RemoteResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * P9-E2 cloud part. Collects every document id known anywhere (Drive files, Firestore documents, reading positions,
 * local sync rows), deletes all three kinds of copy for each, then lists the three stores again and only reports
 * success when they are really empty. Every delete is idempotent, so a stopped run is simply started again.
 */
@Singleton
class CloudWipeImpl @Inject constructor(
    private val auth: AuthRepository,
    private val syncDao: SyncItemDao,
    private val inventory: CloudInventory,
    private val remote: RemoteMetadataStore,
    private val reading: RemoteReadingStore,
    private val files: RemoteFileStore,
) : CloudWipe {

    private sealed interface Gathered {
        data class Ids(val ids: Set<String>) : Gathered

        data class Block(val reason: CloudWipeBlock) : Gathered
    }

    override suspend fun wipe(): CloudWipeResult {
        val uid = (auth.state.first() as? AuthState.SignedIn)?.uid ?: return CloudWipeResult.NotSignedIn
        return when (val before = gather(uid)) {
            is Gathered.Block -> CloudWipeResult.Stopped(before.reason)
            is Gathered.Ids -> deleteAll(uid, before.ids + syncDao.allDocumentIds())
        }
    }

    private suspend fun deleteAll(uid: String, ids: Set<String>): CloudWipeResult {
        for (id in ids) {
            val block = deleteOne(uid, id)
            if (block != null) return CloudWipeResult.Stopped(block)
        }
        return when (val after = gather(uid)) {
            is Gathered.Block -> CloudWipeResult.Stopped(after.reason)
            is Gathered.Ids -> {
                val left = CloudWipeResult.Stopped(CloudWipeBlock.NOT_EMPTY_AFTER)
                if (after.ids.isEmpty()) CloudWipeResult.Wiped else left
            }
        }
    }

    /** File first, then the metadata: an interrupted run never leaves a file that no listing can find. */
    private suspend fun deleteOne(uid: String, id: String): CloudWipeBlock? {
        val results = listOf(files.delete(id), remote.deleteDocument(uid, id), reading.deleteReading(uid, id))
        return when {
            RemoteResult.DENIED in results -> CloudWipeBlock.DENIED
            RemoteResult.DEFERRED in results -> CloudWipeBlock.NEEDS_ACCESS
            results.any { it != RemoteResult.OK } -> CloudWipeBlock.OFFLINE_OR_FAILED
            else -> null
        }
    }

    private suspend fun gather(uid: String): Gathered {
        val listing = inventory.listOwned()
        val docs = remote.listDocumentIds(uid)
        val readings = reading.listReadingIds(uid)
        if (listing is CloudListing.Complete && docs != null && readings != null) {
            return Gathered.Ids(listing.files.map { it.documentId }.toSet() + docs + readings)
        }
        val needsAccess = listing is CloudListing.NeedsConsent
        return Gathered.Block(if (needsAccess) CloudWipeBlock.NEEDS_ACCESS else CloudWipeBlock.OFFLINE_OR_FAILED)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudWipeModule {
    @Binds
    abstract fun bindCloudWipe(impl: CloudWipeImpl): CloudWipe

    @Binds
    abstract fun bindLocalWipe(impl: LocalWipeImpl): LocalWipe

    @Binds
    abstract fun bindDeleteRunner(impl: AppScopeDeleteAllDataRunner): DeleteAllDataRunner
}
