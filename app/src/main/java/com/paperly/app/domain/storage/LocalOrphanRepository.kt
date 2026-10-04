package com.paperly.app.domain.storage

data class OrphanScan(val count: Int, val bytes: Long)

data class OrphanCleanResult(val deleted: Int, val failed: Int)

/**
 * Manual clean-up of document files no row refers to (leftovers of interrupted imports/restores). Never automatic:
 * the user looks first ([scan]), then confirms ([clean]). Files that belong to a document, Trash included, are
 * never touched, and very recent files are skipped.
 */
interface LocalOrphanRepository {
    suspend fun scan(): OrphanScan

    /** Re-checks everything itself; never trusts an earlier [scan]. */
    suspend fun clean(): OrphanCleanResult
}
