package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Trash = soft-delete via `deletedAt` (Architecture §৫-ক). Every write is scoped so a retry is a harmless no-op. */
@Dao
interface TrashDao {
    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<DocumentEntity>>

    @Query("SELECT documentId FROM documents WHERE deletedAt IS NOT NULL")
    suspend fun getTrashedIds(): List<String>

    @Query("SELECT COUNT(*) FROM documents WHERE documentId = :id AND deletedAt IS NOT NULL")
    suspend fun countTrashed(id: String): Int

    @Query("UPDATE documents SET deletedAt = :now, updatedAt = :now WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long): Int

    @Query("UPDATE documents SET deletedAt = NULL, updatedAt = :now WHERE documentId = :id AND deletedAt IS NOT NULL")
    suspend fun restore(id: String, now: Long): Int

    /** Only ever removes a row that is already in Trash. */
    @Query("DELETE FROM documents WHERE documentId = :id AND deletedAt IS NOT NULL")
    suspend fun deleteTrashedRow(id: String): Int
}
