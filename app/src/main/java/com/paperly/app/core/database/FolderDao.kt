package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Folder table (Architecture §২.১). `parentFolderId` is reserved for nesting; unused in P1.
 * `type` (v12) = the document type the folder belongs to (one Library tab); null = shown in every tab.
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val folderId: String,
    val name: String,
    val parentFolderId: String? = null,
    val createdAt: Long,
    val type: String? = null,
)

@Dao
abstract class FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    abstract suspend fun getAll(): List<FolderEntity>

    /** IGNORE = idempotent retry; returns -1 if the id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entity: FolderEntity): Long

    @Query("SELECT COUNT(*) FROM folders WHERE folderId = :id")
    abstract suspend fun countById(id: String): Int

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :id")
    abstract suspend fun unassignDocuments(id: String): Int

    @Query("UPDATE folders SET name = :name WHERE folderId = :id")
    abstract suspend fun rename(id: String, name: String): Int

    @Query("DELETE FROM folders WHERE folderId = :id")
    abstract suspend fun delete(id: String): Int

    /** Documents are never deleted with their folder: they just return to "no folder", atomically. */
    @Transaction
    open suspend fun deleteAndUnassign(id: String) {
        unassignDocuments(id)
        delete(id)
    }
}
