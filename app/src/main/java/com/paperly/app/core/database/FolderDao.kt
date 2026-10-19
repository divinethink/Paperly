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
 * Folder table (Architecture §২.১). `parentFolderId` = the folder it sits in (null = top level);
 * the nesting limit lives in the domain layer.
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

    /** Moves every document of folder [id] to [newFolder] (null = "no folder"). */
    @Query("UPDATE documents SET folderId = :newFolder WHERE folderId = :id")
    abstract suspend fun moveDocuments(id: String, newFolder: String?): Int

    @Query("UPDATE folders SET name = :name WHERE folderId = :id")
    abstract suspend fun rename(id: String, name: String): Int

    @Query("DELETE FROM folders WHERE folderId = :id")
    abstract suspend fun delete(id: String): Int

    @Query("UPDATE folders SET parentFolderId = :newParent WHERE parentFolderId = :id")
    abstract suspend fun reparentChildren(id: String, newParent: String?): Int

    /**
     * Nothing is deleted but the folder itself: its documents and subfolders move up to [parentId]
     * (null = top level / "no folder"), atomically.
     */
    @Transaction
    open suspend fun deleteAndLift(id: String, parentId: String?) {
        moveDocuments(id, parentId)
        reparentChildren(id, parentId)
        delete(id)
    }

    /** Removes [ids] (a folder and its subfolders). Documents are only un-assigned (callers trash them first). */
    @Transaction
    open suspend fun deleteSubtree(ids: List<String>) {
        ids.forEach {
            moveDocuments(it, null)
            delete(it)
        }
    }
}
