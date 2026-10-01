package com.paperly.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Backup/Restore access to the per-document reader data (progress, bookmarks, annotations).
 * Reads are whole-table on purpose (no LIMIT — a truncated backup would be silent data loss).
 * Inserts are IGNORE only: Restore never overwrites existing rows and a retry never duplicates.
 */
@Dao
abstract class BackupExtrasDao {
    @Query("SELECT * FROM reading_state")
    abstract suspend fun getAllReadingState(): List<ReadingStateEntity>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt")
    abstract suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM annotations ORDER BY createdAt")
    abstract suspend fun getAllAnnotations(): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertReadingState(entity: ReadingStateEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBookmarks(entities: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAnnotations(entities: List<AnnotationEntity>)

    /** One transaction per document: all of its extras are written, or none (the document itself stays). */
    @Transaction
    open suspend fun restoreFor(
        state: ReadingStateEntity?,
        bookmarks: List<BookmarkEntity>,
        annotations: List<AnnotationEntity>,
    ) {
        if (state != null) insertReadingState(state)
        insertBookmarks(bookmarks)
        insertAnnotations(annotations)
    }
}
