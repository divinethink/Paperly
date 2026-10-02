package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Architecture §২.১ ReadingState. `locator` = PDF page-index string. CASCADE: row goes with its document. */
@Entity(
    tableName = "reading_state",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingStateEntity(
    @PrimaryKey val documentId: String,
    val locator: String,
    val progressPercent: Float,
    val lastOpenedAt: Long,
)

/** Architecture §২.১ Bookmark. Unique (documentId, locator): one bookmark per page. */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "locator"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey val bookmarkId: String,
    val documentId: String,
    val locator: String,
    val title: String? = null,
    val createdAt: Long,
)

@Dao
abstract class ReaderDao {
    /** REPLACE on the child row only: idempotent upsert, never touches the document. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertState(entity: ReadingStateEntity)

    @Query("SELECT * FROM reading_state WHERE documentId = :id")
    abstract suspend fun getState(id: String): ReadingStateEntity?

    @Query("SELECT progressPercent FROM reading_state WHERE documentId = :id")
    abstract fun observeProgress(id: String): Flow<Float?>

    @Query("SELECT locator FROM bookmarks WHERE documentId = :id")
    abstract fun observeBookmarkLocators(id: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBookmark(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE documentId = :id AND locator = :locator")
    abstract suspend fun deleteBookmark(id: String, locator: String): Int

    /** Atomic toggle. Returns true if the bookmark now exists. */
    @Transaction
    open suspend fun toggleBookmark(entity: BookmarkEntity): Boolean {
        if (deleteBookmark(entity.documentId, entity.locator) > 0) return false
        insertBookmark(entity)
        return true
    }
}
