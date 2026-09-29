package com.paperly.app.domain.trash

import com.paperly.app.domain.document.Document
import kotlinx.coroutines.flow.Flow

interface TrashRepository {
    fun observeTrash(): Flow<List<Document>>

    /** Soft-delete: the file stays on disk, so Restore is lossless. */
    suspend fun trash(id: String)

    suspend fun restore(id: String)

    /** Irreversible. Only acts on documents already in Trash; false if the file or row could not be removed. */
    suspend fun deleteForever(id: String): Boolean

    /** Deletes every trashed document; true only if all succeeded. */
    suspend fun emptyTrash(): Boolean
}
