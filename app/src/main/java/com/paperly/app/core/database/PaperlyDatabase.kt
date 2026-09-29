package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.paperly.app.core.model.StorageState
import kotlinx.coroutines.flow.Flow

const val DATABASE_NAME = "paperly.db"
const val DATABASE_VERSION = 2

/** Local Document table. Fields follow Architecture §২.১; times are UTC epoch millis. */
@Entity(
    tableName = "documents",
    indices = [Index("checksum"), Index("folderId"), Index("deletedAt")],
)
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val title: String,
    val type: String,
    val sizeBytes: Long,
    val checksum: String,
    val localUri: String? = null,
    val cloudRef: String? = null,
    val storageState: String = StorageState.LOCAL_ONLY,
    val isPasswordProtected: Boolean = false,
    val folderId: String? = null,
    val tags: List<String>? = null,
    val isFavorite: Boolean = false,
    val isSensitive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
    val deletedAt: Long? = null,
    val convertedFrom: String? = null,
    val schemaVersion: Int = 1,
)

class Converters {
    @TypeConverter
    fun fromTags(value: List<String>?): String? = value?.joinToString(TAG_SEPARATOR)

    @TypeConverter
    fun toTags(value: String?): List<String>? =
        value?.let { if (it.isEmpty()) emptyList() else it.split(TAG_SEPARATOR) }

    private companion object {
        const val TAG_SEPARATOR = "\u001F"
    }
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE documentId = :id")
    suspend fun getById(id: String): DocumentEntity?

    /** Duplicate detection by content checksum (indexed); trashed rows are not duplicates. */
    @Query("SELECT * FROM documents WHERE checksum = :checksum AND deletedAt IS NULL LIMIT 1")
    suspend fun findActiveByChecksum(checksum: String): DocumentEntity?

    @Query("UPDATE documents SET title = :title, updatedAt = :now WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun updateTitle(id: String, title: String, now: Long): Int

    @Query("UPDATE documents SET isFavorite = :favorite WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun updateFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE documents SET lastOpenedAt = :now WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun updateLastOpened(id: String, now: Long): Int

    /** Organizing metadata: no updatedAt bump on purpose (keeps list order stable, like favorite). */
    @Query("UPDATE documents SET folderId = :folderId WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun updateFolder(id: String, folderId: String?): Int

    @Query("UPDATE documents SET tags = :tags WHERE documentId = :id AND deletedAt IS NULL")
    suspend fun updateTags(id: String, tags: String?): Int

    /** IGNORE = idempotent: a retry/double-tap never overwrites an existing row. Returns -1 if ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DocumentEntity): Long
}

@Database(
    entities = [DocumentEntity::class, FolderEntity::class],
    version = DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PaperlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun trashDao(): TrashDao

    abstract fun folderDao(): FolderDao
}
