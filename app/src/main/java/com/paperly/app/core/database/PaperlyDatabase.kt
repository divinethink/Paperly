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

    /** IGNORE = idempotent: a retry/double-tap never overwrites an existing row. Returns -1 if ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DocumentEntity): Long
}

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class PaperlyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
