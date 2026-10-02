package com.paperly.app.domain.storage

import kotlinx.coroutines.flow.Flow

/** Sizes are the stored document sizes (metadata sum); read-only, never touches files. */
data class StorageUsage(
    val activeBytes: Long,
    val activeCount: Int,
    val trashBytes: Long,
    val trashCount: Int,
) {
    val totalBytes: Long get() = activeBytes + trashBytes
}

interface StorageUsageRepository {
    fun observeUsage(): Flow<StorageUsage>

    /** Groups of active document ids with identical content (each group has 2+ ids). */
    fun observeDuplicateGroups(): Flow<List<List<String>>>
}
