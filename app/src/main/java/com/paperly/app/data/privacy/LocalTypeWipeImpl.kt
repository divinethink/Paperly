package com.paperly.app.data.privacy

import com.paperly.app.core.database.AggregateDao
import com.paperly.app.core.database.SyncItemDao
import com.paperly.app.core.database.TrashDao
import com.paperly.app.core.file.DocumentFileStore
import com.paperly.app.data.scanner.ScanSourceStore
import com.paperly.app.domain.document.LibraryType
import com.paperly.app.domain.privacy.LocalTypeWipe
import com.paperly.app.domain.privacy.ScopedDeleteOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes the live documents of one kind from this phone. Per document: file first, row second (a failure in
 * between leaves a normal Library row that can be deleted again, never an invisible orphan file). Reading
 * progress, bookmarks, annotations and scan rows go with the row (FK cascade); kept scan pages are removed too.
 * The cloud is not touched: queued uploads/deletes for the document are dropped so nothing is sent for it.
 */
@Singleton
class LocalTypeWipeImpl @Inject constructor(
    private val aggregate: AggregateDao,
    private val trashDao: TrashDao,
    private val syncDao: SyncItemDao,
    private val fileStore: DocumentFileStore,
    private val scanStore: ScanSourceStore,
) : LocalTypeWipe {

    override suspend fun wipe(type: LibraryType): ScopedDeleteOutcome {
        val ids = aggregate.getAllActive().filter { it.type == type.docType }.map { it.documentId }
        val failed = ids.count { !removeOne(it) }
        val deleted = ids.size - failed
        return if (failed == 0) ScopedDeleteOutcome.Done(deleted) else ScopedDeleteOutcome.Partial(deleted, failed)
    }

    private suspend fun removeOne(id: String): Boolean {
        val fileGone = withContext(Dispatchers.IO) { fileStore.resolve(id) == null } || fileStore.delete(id)
        val deleted = fileGone && trashDao.deleteActiveRow(id) > 0
        if (deleted) {
            withContext(Dispatchers.IO) { runCatching { scanStore.deleteAll(id) } }
            syncDao.dropItemsFor(id)
            syncDao.deleteBase(id)
        }
        return deleted
    }
}
